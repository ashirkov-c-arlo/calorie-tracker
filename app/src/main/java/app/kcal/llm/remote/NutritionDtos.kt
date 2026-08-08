package app.kcal.llm.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes of `docs/llm-proxy-contract.md`. Response fields are nullable on purpose: a
 * missing required field is a hard-invalid payload the app rejects itself instead of letting
 * deserialization throw, and an unknown `type` must map to `UNKNOWN` rather than crash.
 */
@Serializable
internal data class ParseRequestDto(
    val text: String,
    val image: ImageDto? = null,
    val clarification: ClarificationDto? = null,
)

@Serializable
internal data class ImageDto(
    @SerialName("media_type") val mediaType: String = JPEG_MEDIA_TYPE,
    @SerialName("data_base64") val dataBase64: String,
)

internal const val JPEG_MEDIA_TYPE = "image/jpeg"

@Serializable
internal data class ClarificationDto(val question: String, val answer: String)

@Serializable
internal data class ParseResponseDto(
    val type: String? = null,
    val items: List<FoodItemDto>? = null,
    val note: String? = null,
    val question: String? = null,
    val code: String? = null,
    val usage: UsageDto? = null,
)

@Serializable
internal data class FoodItemDto(
    val name: String? = null,
    val grams: Double? = null,
    val kcal: Int? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    val confidence: Float? = null,
)

@Serializable
internal data class UsageDto(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
)
