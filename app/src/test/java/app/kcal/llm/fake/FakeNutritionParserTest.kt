package app.kcal.llm.fake

import app.kcal.llm.FailureReason
import app.kcal.llm.ParseResult
import app.kcal.llm.UserInput
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The debug stub has to stay deterministic: the whole entry flow is verified through it. */
class FakeNutritionParserTest {

    private val parser = FakeNutritionParser()

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
                    clarification =
                    app.kcal.llm.ClarificationAnswer(question = question.question, answer = "250 г"),
                ),
            )

        assertTrue(answered is ParseResult.Success)
        assertEquals(1, answered.items.size)
    }

    @Test
    fun `plain text yields one reviewable item`() = runTest {
        val result = parser.parse(UserInput.Text("oatmeal with milk")) as ParseResult.Success

        assertEquals("oatmeal with milk", result.items.single().name)
        assertEquals(250, result.items.single().macros.kcal)
    }
}
