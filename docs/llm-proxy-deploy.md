# LXC deployment without Docker

This guide covers the guest OS only. It assumes a Debian/Ubuntu-based LXC, a DNS name
pointing at the service, and inbound TCP 80/443 reaching the container.

## Configuration map

| Value | Location | Sent to Android |
|---|---|---:|
| AWS access key and secret | `/etc/kcal-proxy/kcal-proxy.env` only | no |
| Bedrock invocation IDs | `/etc/kcal-proxy/kcal-proxy.env` only | no |
| Proxy API key | `/etc/kcal-proxy/kcal-proxy.env` and Android `local.properties` | yes |
| Public HTTPS URL | Caddyfile and Android `local.properties` | yes |
| SQLite quota database | `/var/lib/kcal-proxy/kcal_proxy.sqlite3` | no |

Never commit the environment file or Android `local.properties`.

## 1. Install and deploy

Run as root or through `sudo`:

```bash
apt update
apt install -y ca-certificates caddy git python3 python3-venv

useradd --system --home /var/lib/kcal-proxy --shell /usr/sbin/nologin kcal-proxy
install -d -o kcal-proxy -g kcal-proxy -m 0750 /var/lib/kcal-proxy

git clone <repository-url> /opt/calorie-tracker
cd /opt/calorie-tracker
git checkout --detach <release-tag-or-commit>

python3 -m venv /opt/kcal-proxy-venv
/opt/kcal-proxy-venv/bin/pip install --upgrade pip
/opt/kcal-proxy-venv/bin/pip install -r /opt/calorie-tracker/llm-proxy/requirements.txt

cd /opt/calorie-tracker/llm-proxy
/opt/kcal-proxy-venv/bin/python -m unittest discover -s tests -t .
```

Deploy a clean commit or tag, not a working directory containing local files.

## 2. Select Bedrock models

Run discovery from an administrator machine with AWS CLI credentials:

```bash
aws bedrock list-inference-profiles \
  --region eu-west-1 \
  --type-equals SYSTEM_DEFINED \
  --query 'inferenceProfileSummaries[].[inferenceProfileId,status]' \
  --output table

aws bedrock get-inference-profile \
  --region eu-west-1 \
  --inference-profile-identifier '<inference-profile-id>' \
  --query '{arn:inferenceProfileArn,status:status,models:models[].modelArn}'
```

Use the returned `inferenceProfileId`, not a catalog model ID:

- `MODEL_TEXT`: default model for text parsing; required.
- `MODEL_VISION`: model for text plus JPEG; set it to `MODEL_TEXT` only if that model
  supports vision.
- `MODEL_FALLBACK`: optional model used only after retryable primary-model failures; leave
  empty unless it has also passed evaluation.

Before selecting the text default, run the committed evaluation harness from the
administrator machine where the AWS CLI profile is configured:

```bash
cd <repository-checkout>/llm-proxy

python run_eval.py \
  --profile <aws-cli-profile> \
  --models haiku45 \
  --model-id 'haiku45=<candidate-inference-profile-id>' \
  --limit 5 \
  --concurrency 1

python run_eval.py \
  --profile <aws-cli-profile> \
  --models haiku45 \
  --model-id 'haiku45=<candidate-inference-profile-id>' \
  --concurrency 2
```

The full eval calls Bedrock directly 100 times and bypasses proxy quotas. Select a model
only when every hard gate in `results/summary.md` passes. Validate `MODEL_VISION` with the
photo smoke test in step 7.

## 3. Create the AWS runtime identity

Use a dedicated AWS identity, never root credentials. It needs `bedrock:InvokeModel` on:

1. each configured inference-profile ARN;
2. every foundation-model ARN listed by `get-inference-profile` for those profiles.

Minimal policy shape:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "bedrock:InvokeModel",
      "Resource": [
        "<inference-profile-arn>",
        "<foundation-model-arn-1>",
        "<foundation-model-arn-2>"
      ]
    }
  ]
}
```

Include the resources for text, vision, and fallback models. The proxy does not use
streaming and does not need `bedrock:InvokeModelWithResponseStream`.

Ensure Bedrock model invocation logging is disabled because it can export meal text and
images:

```bash
aws bedrock get-model-invocation-logging-configuration --region eu-west-1
```

## 4. Create the proxy environment file

Generate the proxy API key:

```bash
python3 -c 'import secrets; print(secrets.token_urlsafe(32))'
```

Create the root-readable environment file:

```bash
install -d -m 0750 /etc/kcal-proxy
install -m 0600 /dev/null /etc/kcal-proxy/kcal-proxy.env
editor /etc/kcal-proxy/kcal-proxy.env
```

Contents:

```dotenv
API_KEYS=<generated-proxy-api-key>

AWS_REGION=eu-west-1
AWS_ACCESS_KEY_ID=<dedicated-runtime-access-key-id>
AWS_SECRET_ACCESS_KEY=<dedicated-runtime-secret-access-key>
AWS_EC2_METADATA_DISABLED=true

MODEL_TEXT=<text-inference-profile-id>
MODEL_VISION=<vision-inference-profile-id>
MODEL_FALLBACK=

ENABLED=true
DAILY_REQUEST_CAP=100
MONTHLY_REQUEST_CAP=3000
PER_IP_DAILY_CAP=40
RATE_PER_SECOND=2
RATE_BURST=5

TRUST_FORWARDED_FOR=true
HOST=127.0.0.1
PORT=8080
DB_PATH=/var/lib/kcal-proxy/kcal_proxy.sqlite3
REQUEST_DEADLINE_S=24
```

Add `AWS_SESSION_TOKEN` only when using temporary credentials. Temporary credentials need
an external renewal mechanism and are not suitable for an unattended service otherwise.

## 5. Create the systemd service

Create `/etc/systemd/system/kcal-proxy.service`:

```ini
[Unit]
Description=Kcal LLM proxy
Wants=network-online.target
After=network-online.target

[Service]
Type=simple
User=kcal-proxy
Group=kcal-proxy
WorkingDirectory=/opt/calorie-tracker/llm-proxy
EnvironmentFile=/etc/kcal-proxy/kcal-proxy.env
Environment=PYTHONUNBUFFERED=1
Environment=PYTHONDONTWRITEBYTECODE=1
ExecStart=/opt/kcal-proxy-venv/bin/python -m kcal_proxy
Restart=on-failure
RestartSec=5
UMask=0077
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadWritePaths=/var/lib/kcal-proxy

[Install]
WantedBy=multi-user.target
```

Enable it:

```bash
systemctl daemon-reload
systemctl enable --now kcal-proxy
systemctl status kcal-proxy
curl -s http://127.0.0.1:8080/healthz
journalctl -u kcal-proxy -f
```

A healthy response only proves that the process started. Step 7 performs real Bedrock
calls.

## 6. Configure HTTPS with Caddy

Merge the global options into `/etc/caddy/Caddyfile`; do not create a second global block
if one already exists:

```caddy
{
    servers {
        timeouts {
            read_header 5s
            read_body 10s
        }
    }
}

kcal.example.net {
    request_body {
        max_size 2MB
    }
    reverse_proxy 127.0.0.1:8080
}
```

Replace the domain, then validate and reload:

```bash
caddy validate --config /etc/caddy/Caddyfile
systemctl enable --now caddy
systemctl reload caddy
curl -s https://kcal.example.net/healthz
```

Keep port 8080 bound to localhost. Set `TRUST_FORWARDED_FOR=true` only because Caddy is the
controlled reverse proxy in front of it.

### Certificate from a local CA instead of ACME

When a local CA issues the certificate, point `tls` at the files; Caddy then never contacts
an ACME server. The certificate file holds the leaf first, then every intermediate, and the
repository keeps that bundle uncommitted in `llm-proxy/tls/fullchain.pem` (the directory is
gitignored, because a leaf carries the real host name and the issuing account's e-mail).

```bash
install -d -o caddy -g caddy -m 0750 /etc/caddy/tls
install -o caddy -g caddy -m 0644 fullchain.pem /etc/caddy/tls/fullchain.pem
install -o caddy -g caddy -m 0600 privkey.pem   /etc/caddy/tls/privkey.pem
```

```caddy
kcal.example.net {
    tls /etc/caddy/tls/fullchain.pem /etc/caddy/tls/privkey.pem
    request_body {
        max_size 2MB
    }
    reverse_proxy 127.0.0.1:8080
}
```

The private key never enters the repository. A wildcard leaf for `*.example.net` covers
`kcal.example.net` but not `example.net` itself, so the site address must match the name in
the certificate's subject alternative name. Renewal is a file replacement plus
`systemctl reload caddy`; check the expiry with
`openssl x509 -in /etc/caddy/tls/fullchain.pem -noout -dates`.

Every client must trust the CA's **root** certificate, which is deliberately absent from the
served chain. For the smoke tests in step 7, pass it through the OpenSSL environment instead
of disabling verification:

```bash
SSL_CERT_FILE=/etc/caddy/tls/root-ca.pem \
  /opt/kcal-proxy-venv/bin/python scripts/smoke.py \
  --base-url https://kcal.example.net --api-key '<generated-proxy-api-key>'
```

## 7. Run live smoke tests

Use the same proxy key stored in `/etc/kcal-proxy/kcal-proxy.env`:

```bash
cd /opt/calorie-tracker/llm-proxy

/opt/kcal-proxy-venv/bin/python scripts/smoke.py \
  --base-url https://kcal.example.net \
  --api-key '<generated-proxy-api-key>'

/opt/kcal-proxy-venv/bin/python scripts/smoke.py \
  --base-url https://kcal.example.net \
  --api-key '<generated-proxy-api-key>' \
  --photo /tmp/throwaway-test.jpg
```

Expected result: `0 failure(s)`. Use a real JPEG no larger than 1 MiB, with no personal
content.

## 8. Configure the Android app

On the Android development machine, set uncommitted `local.properties`:

```properties
LLM_API_BASE_URL=https://kcal.example.net
LLM_API_KEY=<same-generated-proxy-api-key>
```

Do not append `/v1`. Rebuild the APK because both values are compiled into `BuildConfig`.
Never put AWS credentials or model IDs in the Android project.

With a certificate from a local CA, install that CA's root certificate on the device:
copy `root-ca.pem` to it, then **Settings → Security → More security settings → Encryption &
credentials → Install a certificate → CA certificate**. Debug builds trust user-installed CAs
through `app/src/main/res/xml/network_security_config.xml`; release builds deliberately do
not, so a release APK needs a publicly trusted certificate.

## 9. Change the default model

Edit only the server environment file:

```bash
editor /etc/kcal-proxy/kcal-proxy.env
# Change MODEL_TEXT and, when needed, MODEL_VISION or MODEL_FALLBACK.

systemctl restart kcal-proxy
journalctl -u kcal-proxy -n 50 --no-pager
```

Run both smoke tests again. A model change never requires an Android release.

## 10. Update the proxy

```bash
cd /opt/calorie-tracker
git fetch --all --prune
git checkout --detach <new-release-tag-or-commit>

/opt/kcal-proxy-venv/bin/pip install -r llm-proxy/requirements.txt
cd llm-proxy
/opt/kcal-proxy-venv/bin/python -m unittest discover -s tests -t .
systemctl restart kcal-proxy
```

Then repeat step 7.
