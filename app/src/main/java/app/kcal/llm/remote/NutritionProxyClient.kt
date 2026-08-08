package app.kcal.llm.remote

import app.kcal.core.common.AppLocaleProvider
import app.kcal.domain.usecase.ValidateMeal
import app.kcal.llm.FailureReason
import app.kcal.llm.NutritionParser
import app.kcal.llm.ParseResult
import app.kcal.llm.UserInput
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Base URL and routing key from `BuildConfig`; both are empty until a proxy is configured. */
data class LlmProxyConfig(val baseUrl: String, val apiKey: String)

/**
 * The app's only nutrition transport. Retries are owned by the proxy, so this client never
 * loops: a failure surfaces as an explicit Retry action in the UI. Raw bodies are never
 * logged or shown.
 */
@Singleton
class NutritionProxyClient @Inject constructor(
    private val httpClient: HttpClient,
    private val config: LlmProxyConfig,
    private val localeProvider: AppLocaleProvider,
    private val validateMeal: ValidateMeal,
) : NutritionParser {

    override suspend fun parse(input: UserInput): ParseResult {
        if (config.baseUrl.isBlank() || input.text.isBlank()) {
            return ParseResult.Failure(FailureReason.INVALID_REQUEST)
        }
        // TODO(stage 5): transport the transient JPEG once downscaling and Base64 encoding exist.
        if (input is UserInput.TextWithPhoto) return ParseResult.Failure(FailureReason.INVALID_REQUEST)
        return try {
            val response =
                httpClient.post("${config.baseUrl.trimEnd('/')}$PARSE_PATH") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.AcceptLanguage, localeProvider.current().language)
                    header(API_KEY_HEADER, config.apiKey)
                    setBody(input.toRequestDto())
                }
            val body = response.contractBodyOrNull()
            if (response.status.isSuccess()) {
                body?.toParseResult(validateMeal) ?: ParseResult.Failure(FailureReason.INVALID_RESPONSE)
            } else {
                // The contract code wins; a gateway response without one falls back to its status.
                ParseResult.Failure(failureReasonOfCode(body?.code) ?: failureReasonOfStatus(response.status.value))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (timeout: HttpRequestTimeoutException) {
            ParseResult.Failure(FailureReason.TIMEOUT, timeout)
        } catch (timeout: ConnectTimeoutException) {
            ParseResult.Failure(FailureReason.TIMEOUT, timeout)
        } catch (timeout: SocketTimeoutException) {
            ParseResult.Failure(FailureReason.TIMEOUT, timeout)
        } catch (offline: IOException) {
            ParseResult.Failure(FailureReason.NO_NETWORK, offline)
        }
    }

    /** Null whenever the payload is not the contract shape, whatever the status was. */
    private suspend fun HttpResponse.contractBodyOrNull(): ParseResponseDto? = try {
        body<ParseResponseDto>()
    } catch (malformed: SerializationException) {
        null
    } catch (unconvertible: ContentConvertException) {
        null
    } catch (foreignContentType: NoTransformationFoundException) {
        null
    }

    private companion object {
        const val PARSE_PATH = "/v1/nutrition/parse"
        const val API_KEY_HEADER = "X-Api-Key"
    }
}

internal fun UserInput.toRequestDto(): ParseRequestDto = ParseRequestDto(
    text = text,
    clarification = clarification?.let { ClarificationDto(question = it.question, answer = it.answer) },
)
