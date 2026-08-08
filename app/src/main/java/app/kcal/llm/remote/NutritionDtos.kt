package app.kcal.llm.remote

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Wire shapes of `docs/llm-proxy-contract.md`. Required fields have no defaults on purpose:
 * an absent key then fails deserialization, which the client maps to `INVALID_RESPONSE`
 * instead of silently reading it as "unknown". A nullable type still accepts an explicit
 * `null`, which is what the contract allows for `grams` and `note`.
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

@Serializable(with = ParseResponseSerializer::class)
internal sealed interface ParseResponseDto {

    @Serializable
    data class Success(val items: List<FoodItemDto>, val note: String?, val usage: UsageDto? = null) : ParseResponseDto

    @Serializable
    data class Clarification(val question: String, val usage: UsageDto? = null) : ParseResponseDto

    @Serializable
    data class Error(val code: String) : ParseResponseDto

    /** A missing or unrecognised `type`. Contract §9 maps it to `UNKNOWN`. */
    @Serializable
    data object Unknown : ParseResponseDto
}

/**
 * The envelope is discriminated by `type`, but an unknown value must become a mapped
 * `UNKNOWN` failure rather than a deserialization error, so the discriminator is selected by
 * hand instead of using sealed polymorphism.
 */
internal object ParseResponseSerializer :
    JsonContentPolymorphicSerializer<ParseResponseDto>(ParseResponseDto::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ParseResponseDto> =
        when (((element as? JsonObject)?.get("type") as? JsonPrimitive)?.takeIf { it.isString }?.content) {
            "success" -> ParseResponseDto.Success.serializer()
            "clarification" -> ParseResponseDto.Clarification.serializer()
            "error" -> ParseResponseDto.Error.serializer()
            else -> ParseResponseDto.Unknown.serializer()
        }
}

@Serializable
internal data class FoodItemDto(
    val name: String,
    val grams: Double?,
    val kcal: Int,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("fat_g") val fatG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    val confidence: Float,
)

@Serializable
internal data class UsageDto(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int,
)
