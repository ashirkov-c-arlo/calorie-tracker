package app.kcal.llm.fake

import app.kcal.core.common.AppLocaleProvider
import app.kcal.llm.ClarificationAnswer
import app.kcal.llm.FailureReason
import app.kcal.llm.ParseResult
import app.kcal.llm.UserInput
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The debug stub has to stay deterministic: the whole entry flow is verified through it. */
class FakeNutritionParserTest {

    private val parser = FakeNutritionParser(AppLocaleProvider { Locale.US })

    @Test
    fun `blank text is refused`() = runTest {
        assertEquals(
            FailureReason.INVALID_REQUEST,
            (parser.parse(UserInput.Text(" ")) as ParseResult.Failure).reason,
        )
    }

    @Test
    fun `a question asks once and then succeeds with the answer`() = runTest {
        val question = parser.parse(UserInput.Text("сколько в порции риса?"))
        assertTrue(question is ParseResult.NeedsClarification)

        val answered =
            parser.parse(
                UserInput.Text(
                    text = "сколько в порции риса?",
                    clarification = ClarificationAnswer(question = question.question, answer = "250 г"),
                ),
            )

        assertTrue(answered is ParseResult.Success)
        assertEquals(1, answered.items.size)
    }

    @Test
    fun `plain text yields one item in the interface language`() = runTest {
        val result = parser.parse(UserInput.Text("oatmeal with milk")) as ParseResult.Success

        assertEquals("Sample dish", result.items.single().name)
        assertEquals(250, result.items.single().macros.kcal)
    }

    @Test
    fun `a photo request is parsed like text, so debug builds can exercise the photo flow`() = runTest {
        val result = parser.parse(UserInput.TextWithPhoto("what is on the plate", "/cache/entry-photos/meal.jpg"))

        assertTrue(result is ParseResult.Success)
        assertEquals("Sample dish", result.items.single().name)
    }

    @Test
    fun `a russian interface answers in russian whatever the input language is`() = runTest {
        val russian = FakeNutritionParser(AppLocaleProvider { Locale.forLanguageTag("ru") })

        val question = russian.parse(UserInput.Text("how much rice?")) as ParseResult.NeedsClarification
        val success = russian.parse(UserInput.Text("oatmeal with milk")) as ParseResult.Success

        assertEquals("Какого примерно размера была порция?", question.question)
        assertEquals("Пробное блюдо", success.items.single().name)
        assertEquals("Значения-заглушки офлайн-парсера.", success.note)
    }

    @Test
    fun `an unsupported interface language falls back to english`() = runTest {
        val french = FakeNutritionParser(AppLocaleProvider { Locale.FRENCH })

        val success = french.parse(UserInput.Text("omelette")) as ParseResult.Success

        assertEquals("Sample dish", success.items.single().name)
    }
}
