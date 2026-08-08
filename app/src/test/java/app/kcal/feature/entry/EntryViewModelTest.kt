package app.kcal.feature.entry

import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.EntrySource
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.SaveMeal
import app.kcal.domain.usecase.ValidateMeal
import app.kcal.llm.FailureReason
import app.kcal.llm.NutritionParser
import app.kcal.llm.ParseResult
import app.kcal.llm.UserInput
import app.kcal.testing.FakeMealRepository
import app.kcal.testing.FakeProfileRepository
import app.kcal.testing.completeProfile
import app.kcal.testing.foodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank text is refused before any request`() = runTest {
        val parser = ScriptedParser()
        val viewModel = viewModel(parser)

        viewModel.onTextChange("   ")
        viewModel.onParse()
        runCurrent()

        assertTrue(viewModel.uiState.value.textMissing)
        assertTrue(parser.inputs.isEmpty())
    }

    @Test
    fun `a successful parse opens the confirmation without persisting anything`() = runTest {
        val repository = FakeMealRepository()
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem(name = "Oatmeal", grams = 250.5)), "note"))
        val viewModel = viewModel(parser, repository, Locale.forLanguageTag("ru"))

        viewModel.onTextChange("овсянка")
        viewModel.onParse()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isParsing)
        assertTrue(state.isConfirming)
        assertEquals("note", state.note)
        assertEquals("Oatmeal", state.items.single().name)
        assertEquals("250,5", state.items.single().grams)
        assertEquals(UserInput.Text("овсянка"), parser.inputs.single())
        assertTrue(repository.meals.value.isEmpty())
    }

    @Test
    fun `confirming stores the parsed source and the raw input`() = runTest {
        val repository = FakeMealRepository()
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem()), null))
        val viewModel = viewModel(parser, repository)
        viewModel.onTextChange("oatmeal with milk")
        viewModel.onParse()
        runCurrent()
        val event = async { viewModel.events.first() }

        viewModel.onConfirm()
        runCurrent()

        assertEquals(EntryEvent.Saved, event.await())
        val meal = repository.meals.value.single()
        assertEquals(EntrySource.LLM_TEXT, meal.source)
        assertEquals("oatmeal with milk", meal.rawUserInput)
        assertEquals(1, repository.targetsEnsured.size)
    }

    @Test
    fun `an edited confirmation saves the edited values`() = runTest {
        val repository = FakeMealRepository()
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem(name = "Oatmeal", kcal = 300)), null))
        val viewModel = viewModel(parser, repository)
        viewModel.onTextChange("oatmeal")
        viewModel.onParse()
        runCurrent()

        val key = viewModel.uiState.value.items.single().key
        viewModel.onItemChange(key, MealItemField.KCAL, "260")
        viewModel.onAddItem()
        viewModel.onRemoveItem(viewModel.uiState.value.items.last().key)
        viewModel.onConfirm()
        runCurrent()

        assertEquals(260, repository.meals.value.single().items.single().macros.kcal)
    }

    @Test
    fun `an incomplete added row blocks the save`() = runTest {
        val repository = FakeMealRepository()
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem()), null))
        val viewModel = viewModel(parser, repository)
        viewModel.onTextChange("oatmeal")
        viewModel.onParse()
        runCurrent()

        viewModel.onAddItem()
        viewModel.onConfirm()
        runCurrent()

        assertEquals(MealItemFieldError.REQUIRED, viewModel.uiState.value.items.last().errors.name)
        assertTrue(repository.meals.value.isEmpty())
        assertTrue(viewModel.uiState.value.isConfirming)
    }

    @Test
    fun `a clarification keeps the text and resubmits it with the answer`() = runTest {
        val parser =
            ScriptedParser(
                ParseResult.NeedsClarification("How large was the serving?"),
                ParseResult.Success(listOf(foodItem()), null),
            )
        val viewModel = viewModel(parser)
        viewModel.onTextChange("chicken with rice")

        viewModel.onParse()
        runCurrent()
        assertEquals("How large was the serving?", viewModel.uiState.value.clarificationQuestion)
        assertEquals("chicken with rice", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.isConfirming)

        viewModel.onClarificationAnswerChange("about 250 g")
        viewModel.onSubmitClarification()
        runCurrent()

        assertEquals(
            UserInput.Text(
                "chicken with rice",
                app.kcal.llm.ClarificationAnswer("How large was the serving?", "about 250 g"),
            ),
            parser.inputs.last(),
        )
        assertTrue(viewModel.uiState.value.isConfirming)
        assertNull(viewModel.uiState.value.clarificationQuestion)
    }

    @Test
    fun `editing the description drops the pending clarification`() = runTest {
        val parser =
            ScriptedParser(
                ParseResult.NeedsClarification("How large was the serving?"),
                ParseResult.Success(listOf(foodItem()), null),
            )
        val viewModel = viewModel(parser)
        viewModel.onTextChange("chicken with rice")
        viewModel.onParse()
        runCurrent()
        viewModel.onClarificationAnswerChange("about 250 g")

        viewModel.onTextChange("chicken with buckwheat")

        assertNull(viewModel.uiState.value.clarificationQuestion)
        assertEquals("", viewModel.uiState.value.clarificationAnswer)

        viewModel.onSubmitClarification()
        runCurrent()
        assertEquals(1, parser.inputs.size)

        viewModel.onParse()
        runCurrent()
        assertEquals(UserInput.Text("chicken with buckwheat"), parser.inputs.last())
    }

    @Test
    fun `retry after editing the description resends only the new text`() = runTest {
        val parser =
            ScriptedParser(
                ParseResult.NeedsClarification("How much?"),
                ParseResult.Failure(FailureReason.TIMEOUT),
                ParseResult.Success(listOf(foodItem()), null),
            )
        val viewModel = viewModel(parser)
        viewModel.onTextChange("rice")
        viewModel.onParse()
        runCurrent()
        viewModel.onClarificationAnswerChange("250 g")
        viewModel.onTextChange("rice and chicken")
        viewModel.onParse()
        runCurrent()

        viewModel.onRetry()
        runCurrent()

        assertEquals(UserInput.Text("rice and chicken"), parser.inputs.last())
    }

    @Test
    fun `an unanswered clarification is not resubmitted`() = runTest {
        val parser = ScriptedParser(ParseResult.NeedsClarification("How much?"))
        val viewModel = viewModel(parser)
        viewModel.onTextChange("rice")
        viewModel.onParse()
        runCurrent()

        viewModel.onSubmitClarification()
        runCurrent()

        assertEquals(1, parser.inputs.size)
    }

    @Test
    fun `a failure keeps the text and retry repeats the same request`() = runTest {
        val parser =
            ScriptedParser(
                ParseResult.Failure(FailureReason.NO_NETWORK),
                ParseResult.Success(listOf(foodItem()), null),
            )
        val viewModel = viewModel(parser)
        viewModel.onTextChange("rice")

        viewModel.onParse()
        runCurrent()
        assertEquals(FailureReason.NO_NETWORK, viewModel.uiState.value.failure)
        assertEquals("rice", viewModel.uiState.value.text)

        viewModel.onRetry()
        runCurrent()

        assertEquals(2, parser.inputs.size)
        assertNull(viewModel.uiState.value.failure)
        assertTrue(viewModel.uiState.value.isConfirming)
    }

    @Test
    fun `retry after a clarification resends the answer`() = runTest {
        val parser =
            ScriptedParser(
                ParseResult.NeedsClarification("How much?"),
                ParseResult.Failure(FailureReason.TIMEOUT),
                ParseResult.Success(listOf(foodItem()), null),
            )
        val viewModel = viewModel(parser)
        viewModel.onTextChange("rice")
        viewModel.onParse()
        runCurrent()
        viewModel.onClarificationAnswerChange("250 g")
        viewModel.onSubmitClarification()
        runCurrent()
        assertEquals(FailureReason.TIMEOUT, viewModel.uiState.value.failure)

        viewModel.onRetry()
        runCurrent()

        assertEquals(3, parser.inputs.size)
        assertEquals("250 g", (parser.inputs.last() as UserInput.Text).clarification?.answer)
    }

    @Test
    fun `a parser crash becomes an unknown failure`() = runTest {
        val viewModel =
            viewModel(
                object : NutritionParser {
                    override suspend fun parse(input: UserInput): ParseResult = error("boom")
                },
            )
        viewModel.onTextChange("rice")

        viewModel.onParse()
        runCurrent()

        assertEquals(FailureReason.UNKNOWN, viewModel.uiState.value.failure)
        assertFalse(viewModel.uiState.value.isParsing)
    }

    @Test
    fun `dismissing the confirmation discards the draft and keeps the text`() = runTest {
        val repository = FakeMealRepository()
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem()), "note"))
        val viewModel = viewModel(parser, repository)
        viewModel.onTextChange("oatmeal")
        viewModel.onParse()
        runCurrent()

        viewModel.onDismissConfirmation()

        val state = viewModel.uiState.value
        assertFalse(state.isConfirming)
        assertTrue(state.items.isEmpty())
        assertNull(state.note)
        assertEquals("oatmeal", state.text)
        assertTrue(repository.meals.value.isEmpty())
    }

    @Test
    fun `a storage failure keeps the draft open`() = runTest {
        val repository = FakeMealRepository().apply { writeFails = true }
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem()), null))
        val viewModel = viewModel(parser, repository)
        viewModel.onTextChange("oatmeal")
        viewModel.onParse()
        runCurrent()

        viewModel.onConfirm()
        runCurrent()

        assertTrue(viewModel.uiState.value.saveFailed)
        assertTrue(viewModel.uiState.value.isConfirming)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `a soft review item stays confirmable`() = runTest {
        val repository = FakeMealRepository()
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem(kcal = 99999)), null))
        val viewModel = viewModel(parser, repository)
        viewModel.onTextChange("pizza")
        viewModel.onParse()
        runCurrent()

        assertTrue(viewModel.uiState.value.items.single().needsReview)
        viewModel.onConfirm()
        runCurrent()

        assertEquals(99999, repository.meals.value.single().items.single().macros.kcal)
    }

    private class ScriptedParser(vararg results: ParseResult) : NutritionParser {
        val inputs = mutableListOf<UserInput>()
        private val scripted = results.toMutableList()

        override suspend fun parse(input: UserInput): ParseResult {
            inputs += input
            return scripted.removeFirstOrNull() ?: ParseResult.Failure(FailureReason.UNKNOWN)
        }
    }

    private fun viewModel(
        parser: NutritionParser,
        mealRepository: FakeMealRepository = FakeMealRepository(),
        locale: Locale = Locale.US,
    ): EntryViewModel {
        val timeProvider =
            TimeProvider(Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneId.of("UTC")), ZoneId.of("UTC"))
        return EntryViewModel(
            nutritionParser = parser,
            saveMeal =
            SaveMeal(
                mealRepository = mealRepository,
                profileRepository = FakeProfileRepository(UserPreferences(profile = completeProfile())),
                calculateDailyTargets = CalculateDailyTargets(),
                validateMeal = ValidateMeal(),
                timeProvider = timeProvider,
            ),
            localeProvider = AppLocaleProvider { locale },
        )
    }
}
