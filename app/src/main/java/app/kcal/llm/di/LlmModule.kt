package app.kcal.llm.di

import app.kcal.BuildConfig
import app.kcal.llm.NutritionParser
import app.kcal.llm.fake.FakeNutritionParser
import app.kcal.llm.remote.LlmProxyConfig
import app.kcal.llm.remote.NutritionProxyClient
import app.kcal.llm.remote.PROXY_CONNECT_TIMEOUT_MILLIS
import app.kcal.llm.remote.PROXY_REQUEST_TIMEOUT_MILLIS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    /** Unknown fields are ignored so the proxy can add optional ones without a client release. */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * No logging plugin: request and response bodies carry user text, so they are never
     * written to a log. Timeouts follow the contract, and the proxy owns upstream retries.
     */
    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = PROXY_REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = PROXY_CONNECT_TIMEOUT_MILLIS
        }
    }

    @Provides
    @Singleton
    fun provideLlmProxyConfig(): LlmProxyConfig =
        LlmProxyConfig(baseUrl = BuildConfig.LLM_API_BASE_URL, apiKey = BuildConfig.LLM_API_KEY)

    /**
     * A debug build without a configured proxy uses the deterministic stub, so the entry flow
     * stays verifiable on a device while the backend does not exist. Release builds always use
     * the real transport.
     */
    @Provides
    @Singleton
    fun provideNutritionParser(
        config: LlmProxyConfig,
        proxyClient: Provider<NutritionProxyClient>,
        fakeParser: Provider<FakeNutritionParser>,
    ): NutritionParser = if (BuildConfig.DEBUG && config.baseUrl.isBlank()) {
        fakeParser.get()
    } else {
        proxyClient.get()
    }
}
