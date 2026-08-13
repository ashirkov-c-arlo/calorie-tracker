package app.kcal.llm.remote

import app.kcal.domain.usecase.ValidateMeal
import app.kcal.domain.usecase.needsReview
import app.kcal.llm.FailureReason
import app.kcal.llm.ParseResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixture-driven contract mapping. Every payload comes from `docs/llm-proxy-contract.md`. */
class ParseResponseMappingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val validateMeal = ValidateMeal()

    @Test
    fun `english success maps every item and keeps grams`() {
        val result = parse("parse_text_success.json")

        val success = result as ParseResult.Success
        assertEquals(listOf("Chicken breast", "Boiled rice"), success.items.map { it.name })
        assertEquals(180.0, success.items.first().grams)
        assertEquals(297, success.items.first().macros.kcal)
        assertEquals(0.91f, success.items.first().confidence)
        assertNull(success.note)
        assertFalse(success.items.any { it.needsReview() })
    }

    @Test
    fun `russian success keeps the localized names, the note and an unknown mass`() {
        val success = parse("parse_text_success_ru.json") as ParseResult.Success

        assertEquals("Омлет из трёх яиц", success.items.first().name)
        assertNull(success.items.last().grams)
        assertEquals("Вес порций оценён по типичной подаче.", success.note)
    }

    @Test
    fun `mixed language input still yields interface-language items`() {
        val success = parse("parse_text_success_mixed_language.json") as ParseResult.Success

        assertEquals(2, success.items.size)
        assertEquals("Buckwheat porridge", success.items.first().name)
    }

    @Test
    fun `photo success maps like text success`() {
        val success = parse("parse_photo_success.json") as ParseResult.Success

        assertEquals(2, success.items.size)
        assertEquals(330, success.items.first().macros.kcal)
    }

    @Test
    fun `clarification carries the question in both languages`() {
        assertEquals(
            ParseResult.NeedsClarification("Approximately how large was the serving?"),
            parse("parse_clarification.json"),
        )
        assertEquals(
            ParseResult.NeedsClarification("Какого примерно размера была порция?"),
            parse("parse_clarification_ru.json"),
        )
    }

    @Test
    fun `a missing required field never deserializes into a plausible value`() {
        assertFailsWith<SerializationException> { decode(readFixture("parse_invalid_schema.json")) }
    }

    @Test
    fun `an absent grams key is rejected while an explicit null means unknown mass`() {
        assertFailsWith<SerializationException> { decode(itemPayload(grams = null)) }

        val success = parseText(itemPayload(grams = "null")) as ParseResult.Success
        assertNull(success.items.single().grams)
    }

    @Test
    fun `an absent note or question or code is rejected`() {
        assertFailsWith<SerializationException> { decode(itemPayload(note = null)) }
        assertFailsWith<SerializationException> { decode("""{"type":"clarification"}""") }
        assertFailsWith<SerializationException> { decode("""{"type":"error"}""") }
    }

    @Test
    fun `a present usage block must carry non-negative counts`() {
        assertFailsWith<SerializationException> { decode(itemPayload(usage = """{"input_tokens":10}""")) }
        assertEquals(
            FailureReason.INVALID_RESPONSE,
            mapped(itemPayload(usage = """{"input_tokens":10,"output_tokens":-1}""")),
        )
        assertTrue(parseText(itemPayload(usage = """{"input_tokens":10,"output_tokens":2}""")) is ParseResult.Success)
    }

    @Test
    fun `empty items is a hard invalid response`() {
        assertEquals(FailureReason.INVALID_RESPONSE, (parse("parse_empty_items.json") as ParseResult.Failure).reason)
    }

    @Test
    fun `an out of range value stays a reviewable draft`() {
        val success = parse("parse_out_of_range.json") as ParseResult.Success

        assertEquals(99999, success.items.single().macros.kcal)
        assertTrue(success.items.single().needsReview())
    }

    @Test
    fun `a contract error code becomes its failure reason`() {
        assertEquals(FailureReason.THROTTLED, (parse("error_throttling.json") as ParseResult.Failure).reason)
    }

    @Test
    fun `an unknown response type or error code is unknown`() {
        assertEquals(FailureReason.UNKNOWN, mapped("""{"type":"digest","code":"QUOTA"}"""))
        assertEquals(FailureReason.UNKNOWN, mapped("""{"type":"error","code":"TEAPOT"}"""))
        assertEquals(FailureReason.UNKNOWN, mapped("{}"))
    }

    @Test
    fun `a clarification without a question is invalid`() {
        assertEquals(FailureReason.INVALID_RESPONSE, mapped("""{"type":"clarification","question":"  "}"""))
    }

    @Test
    fun `negative and out of range invariants are rejected`() {
        assertEquals(FailureReason.INVALID_RESPONSE, mapped(itemPayload(kcal = "-1")))
        assertEquals(FailureReason.INVALID_RESPONSE, mapped(itemPayload(proteinG = "-0.1")))
        assertEquals(FailureReason.INVALID_RESPONSE, mapped(itemPayload(confidence = "1.4")))
        assertEquals(FailureReason.INVALID_RESPONSE, mapped(itemPayload(name = "\" \"")))
    }

    @Test
    fun `unknown fields never fail deserialization`() {
        val payload = """{"type":"clarification","question":"How much?","trace_id":"x","extra":{"a":1}}"""

        assertEquals(ParseResult.NeedsClarification("How much?"), parseText(payload))
    }

    @Test
    fun `the proxy can never claim the app-local offline reason`() {
        assertEquals(FailureReason.UNKNOWN, mapped("""{"type":"error","code":"NO_NETWORK"}"""))
    }

    private fun itemPayload(
        name: String = "\"Oatmeal\"",
        grams: String? = "100.0",
        kcal: String = "300",
        proteinG: String = "10.0",
        confidence: String = "0.5",
        note: String? = "null",
        usage: String? = null,
    ): String {
        val fields =
            listOfNotNull(
                """"name":$name""",
                grams?.let { """"grams":$it""" },
                """"kcal":$kcal""",
                """"protein_g":$proteinG""",
                """"fat_g":6.0""",
                """"carbs_g":50.0""",
                """"confidence":$confidence""",
            )
        val tail = listOfNotNull(note?.let { """"note":$it""" }, usage?.let { """"usage":$it""" })
        return """{"type":"success","items":[{${fields.joinToString(",")}}]${tail.joinToString("") { ",$it" }}}"""
    }

    private fun mapped(payload: String): FailureReason = (parseText(payload) as ParseResult.Failure).reason

    private fun decode(payload: String): ParseResponseDto = json.decodeFromString<ParseResponseDto>(payload)

    private fun parseText(payload: String): ParseResult = decode(payload).toParseResult(validateMeal)

    private fun parse(fixture: String): ParseResult = parseText(readFixture(fixture))

    private fun readFixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("llm/$name")) { "missing fixture $name" }
            .use { it.readBytes().decodeToString() }
}
