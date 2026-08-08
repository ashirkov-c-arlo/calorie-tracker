package app.kcal.llm.remote

import app.kcal.core.common.AppLocaleProvider
import app.kcal.domain.usecase.ValidateMeal
import app.kcal.llm.ClarificationAnswer
import app.kcal.llm.FailureReason
import app.kcal.llm.ParseResult
import app.kcal.llm.UserInput
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.IOException
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Transport behaviour only; no test performs network I/O. */
class NutritionProxyClientTest {

    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `a text request follows the contract shape and headers`() = runTest {
        val client = client(locale = Locale.forLanguageTag("ru")) { respondJson(fixture("parse_text_success.json")) }

        val result = client.parse(UserInput.Text("омлет из трёх яиц"))

        assertTrue(result is ParseResult.Success)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/nutrition/parse", request.url.encodedPath)
        assertEquals("https", request.url.protocol.name)
        assertEquals("ru", request.headers[HttpHeaders.AcceptLanguage])
        assertEquals("test-key", request.headers["X-Api-Key"])
        val body = request.body.toByteArray().decodeToString()
        assertEquals("""{"text":"омлет из трёх яиц"}""", body)
    }

    @Test
    fun `a clarification answer is resubmitted with the original text`() = runTest {
        val client = client { respondJson(fixture("parse_text_success.json")) }

        client.parse(
            UserInput.Text(
                text = "chicken with rice",
                clarification = ClarificationAnswer("How large was the serving?", "About 250 grams"),
            ),
        )

        val body = requests.single().body.toByteArray().decodeToString()
        assertTrue(body.contains(""""question":"How large was the serving?""""))
        assertTrue(body.contains(""""answer":"About 250 grams""""))
        assertFalse(body.contains("image"))
    }

    @Test
    fun `an unsupported interface language still sends a contract language`() = runTest {
        val client = client(locale = Locale.FRENCH) { respondJson(fixture("parse_text_success.json")) }

        client.parse(UserInput.Text("omelette aux trois oeufs"))

        assertEquals("en", requests.single().headers[HttpHeaders.AcceptLanguage])
    }

    @Test
    fun `a contract error body wins over the status code`() = runTest {
        val client = client { respondJson("""{"type":"error","code":"QUOTA"}""", HttpStatusCode.TooManyRequests) }

        assertEquals(FailureReason.QUOTA, failure(client))
    }

    @Test
    fun `a gateway failure without a contract body is unknown, not guessed from the status`() = runTest {
        val client = client { respondError(HttpStatusCode.GatewayTimeout, "<html>gateway timeout</html>") }

        assertEquals(FailureReason.UNKNOWN, failure(client))
    }

    @Test
    fun `only an error envelope with a known code is trusted`() = runTest {
        val cases =
            mapOf(
                """{"type":"error","code":"THROTTLED"}""" to FailureReason.THROTTLED,
                """{"type":"error","code":"TEAPOT"}""" to FailureReason.UNKNOWN,
                """{"type":"error","code":"NO_NETWORK"}""" to FailureReason.UNKNOWN,
                """{"type":"digest","code":"QUOTA"}""" to FailureReason.UNKNOWN,
                """{"type":"error"}""" to FailureReason.UNKNOWN,
            )

        cases.forEach { (payload, reason) ->
            val client = client { respondJson(payload, HttpStatusCode.TooManyRequests) }
            assertEquals(reason, failure(client), payload)
        }
    }

    @Test
    fun `a success envelope on a failed status is never trusted`() = runTest {
        val client =
            client { respondJson(fixture("parse_text_success.json"), HttpStatusCode.ServiceUnavailable) }

        assertEquals(FailureReason.UNKNOWN, failure(client))
    }

    @Test
    fun `an unreadable success body is an invalid response`() = runTest {
        val client = client { respondJson("{ this is not json") }

        assertEquals(FailureReason.INVALID_RESPONSE, failure(client))
    }

    @Test
    fun `a connectivity failure is reported as offline`() = runTest {
        val client = client { throw IOException("no route to host") }

        assertEquals(FailureReason.NO_NETWORK, failure(client))
    }

    @Test
    fun `a missing endpoint or blank text never reaches the network`() = runTest {
        val unconfigured = client(baseUrl = "") { respondJson(fixture("parse_text_success.json")) }
        assertEquals(FailureReason.INVALID_REQUEST, failure(unconfigured))

        val configured = client { respondJson(fixture("parse_text_success.json")) }
        assertEquals(FailureReason.INVALID_REQUEST, failure(configured, UserInput.Text("   ")))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a photo request is refused until the transient image pipeline exists`() = runTest {
        val client = client { respondJson(fixture("parse_text_success.json")) }

        val result = client.parse(UserInput.TextWithPhoto("chicken with rice", "/cache/meal.jpg"))

        assertEquals(FailureReason.INVALID_REQUEST, (result as ParseResult.Failure).reason)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a trailing slash in the configured base url does not duplicate the path`() = runTest {
        val client = client(baseUrl = "https://proxy.invalid/") { respondJson(fixture("parse_clarification.json")) }

        client.parse(UserInput.Text("chicken"))

        assertEquals("/v1/nutrition/parse", requests.single().url.encodedPath)
    }

    private suspend fun failure(
        client: NutritionProxyClient,
        input: UserInput = UserInput.Text("chicken with rice"),
    ): FailureReason = (client.parse(input) as ParseResult.Failure).reason

    private fun client(
        baseUrl: String = "https://proxy.invalid",
        locale: Locale = Locale.US,
        handler: MockRequestHandler,
    ): NutritionProxyClient {
        val engine =
            MockEngine { request ->
                requests += request
                handler(request)
            }
        val httpClient =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return NutritionProxyClient(
            httpClient = httpClient,
            config = LlmProxyConfig(baseUrl = baseUrl, apiKey = "test-key"),
            localeProvider = AppLocaleProvider { locale },
            validateMeal = ValidateMeal(),
        )
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("llm/$name")) { "missing fixture $name" }
            .use { it.readBytes().decodeToString() }
}

private fun MockRequestHandleScope.respondJson(
    payload: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    content = payload,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
