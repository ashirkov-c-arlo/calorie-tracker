package app.kcal.feature.entry

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.DispatcherProvider
import app.kcal.core.common.TimeProvider
import app.kcal.core.common.TransientPhotoStore
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
import app.kcal.testing.resetFileProviderRoots
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
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Robolectric only for the transient photo cache; the flow itself needs no Android state. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EntryViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val photoStore = TransientPhotoStore(context, DispatcherProvider(UnconfinedTestDispatcher()))

    @Before
    fun setUp() {
        resetFileProviderRoots()
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

        assertTrue(viewModel.input.textMissing)
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
        assertEquals("How large was the serving?", viewModel.input.clarificationQuestion)
        assertEquals("chicken with rice", viewModel.input.text)
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
        assertNull(viewModel.input.clarificationQuestion)
    }

    @Test
    fun `a plain parse drops a pending clarification`() = runTest {
        val parser =
            ScriptedParser(
                ParseResult.NeedsClarification("How large was the serving?"),
                ParseResult.Failure(FailureReason.UNKNOWN),
            )
        val viewModel = viewModel(parser)
        viewModel.onTextChange("chicken with rice")
        viewModel.onParse()
        runCurrent()
        viewModel.onClarificationAnswerChange("about 250 g")

        viewModel.onParse()
        runCurrent()

        assertNull(viewModel.input.clarificationQuestion)
        assertEquals("", viewModel.input.clarificationAnswer)
        assertEquals(FailureReason.UNKNOWN, viewModel.input.failure)

        viewModel.onSubmitClarification()
        runCurrent()
        assertEquals(2, parser.inputs.size)
        assertNull((parser.inputs.last() as UserInput.Text).clarification)
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

        assertNull(viewModel.input.clarificationQuestion)
        assertEquals("", viewModel.input.clarificationAnswer)

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
        assertEquals(FailureReason.NO_NETWORK, viewModel.input.failure)
        assertEquals("rice", viewModel.input.text)

        viewModel.onRetry()
        runCurrent()

        assertEquals(2, parser.inputs.size)
        assertNull(viewModel.input.failure)
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
        assertEquals(FailureReason.TIMEOUT, viewModel.input.failure)

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

        assertEquals(FailureReason.UNKNOWN, viewModel.input.failure)
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
        assertEquals("oatmeal", state.inputs.single().text)
        // Reading it again is what the reopened screen offers, so the item is unread once more.
        assertFalse(state.inputs.single().isParsed)
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

    @Test
    fun `an attached photo is parsed as a photo request and stored as a photo entry`() = runTest {
        val repository = FakeMealRepository()
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem()), null))
        val viewModel = viewModel(parser, repository)
        viewModel.onTextChange("what is on the plate")

        viewModel.onPhotoPicked(sourceImage())
        runCurrent()
        val attachedPath = assertNotNull(viewModel.input.photoPath)
        assertTrue(File(attachedPath).exists())

        viewModel.onParse()
        runCurrent()

        assertEquals(UserInput.TextWithPhoto("what is on the plate", attachedPath), parser.inputs.single())
        // Contract §8: a final success drops the photo, so the confirmation no longer offers one.
        assertFalse(File(attachedPath).exists())
        assertNull(viewModel.input.photoPath)

        viewModel.onConfirm()
        runCurrent()
        val meal = repository.meals.value.single()
        assertEquals(EntrySource.LLM_PHOTO, meal.source)
        // The stored record keeps the description only; no path, URI, or image byte reaches it.
        assertEquals("what is on the plate", meal.rawUserInput)
    }

    @Test
    fun `a photo survives a clarification and a retry, and both resend the same file`() = runTest {
        val parser =
            ScriptedParser(
                ParseResult.NeedsClarification("How large was the plate?"),
                ParseResult.Failure(FailureReason.TIMEOUT),
                ParseResult.Success(listOf(foodItem()), null),
            )
        val viewModel = viewModel(parser)
        viewModel.onTextChange("what is on the plate")
        viewModel.onPhotoPicked(sourceImage())
        runCurrent()
        val attachedPath = assertNotNull(viewModel.input.photoPath)

        viewModel.onParse()
        runCurrent()
        assertTrue(File(attachedPath).exists())

        viewModel.onClarificationAnswerChange("about 25 cm")
        viewModel.onSubmitClarification()
        runCurrent()
        assertEquals(FailureReason.TIMEOUT, viewModel.input.failure)
        assertTrue(File(attachedPath).exists())

        viewModel.onRetry()
        runCurrent()

        assertEquals(3, parser.inputs.size)
        parser.inputs.forEach { assertEquals(attachedPath, (it as UserInput.TextWithPhoto).temporaryPhotoPath) }
        assertEquals("about 25 cm", parser.inputs.last().clarification?.answer)
        assertFalse(File(attachedPath).exists())
    }

    @Test
    fun `attaching a photo invalidates a pending clarification`() = runTest {
        val parser = ScriptedParser(ParseResult.NeedsClarification("How large was the plate?"))
        val viewModel = viewModel(parser)
        viewModel.onTextChange("chicken with rice")
        viewModel.onParse()
        runCurrent()
        viewModel.onClarificationAnswerChange("about 250 g")

        viewModel.onPhotoPicked(sourceImage())
        runCurrent()

        assertNull(viewModel.input.clarificationQuestion)
        assertEquals("", viewModel.input.clarificationAnswer)
    }

    @Test
    fun `an unreadable image is reported and leaves the request text only`() = runTest {
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem()), null))
        val viewModel = viewModel(parser)
        viewModel.onTextChange("chicken with rice")

        viewModel.onPhotoPicked(Uri.fromFile(File(context.filesDir, "missing.jpg")))
        runCurrent()

        assertTrue(viewModel.input.photoFailed)
        assertNull(viewModel.input.photoPath)

        viewModel.onParse()
        runCurrent()
        assertEquals(UserInput.Text("chicken with rice"), parser.inputs.single())
    }

    @Test
    fun `removing the photo deletes it and keeps the typed text`() = runTest {
        val viewModel = viewModel(ScriptedParser())
        viewModel.onTextChange("what is on the plate")
        viewModel.onPhotoPicked(sourceImage())
        runCurrent()
        val attachedPath = assertNotNull(viewModel.input.photoPath)

        viewModel.onRemovePhoto()

        assertFalse(File(attachedPath).exists())
        assertNull(viewModel.input.photoPath)
        assertEquals("what is on the plate", viewModel.input.text)
    }

    @Test
    fun `leaving the flow deletes the attached photo`() = runTest {
        val viewModel = viewModel(ScriptedParser())
        viewModel.onTextChange("what is on the plate")
        viewModel.onPhotoPicked(sourceImage())
        runCurrent()
        val attachedPath = assertNotNull(viewModel.input.photoPath)

        viewModel.onCleared()

        assertFalse(File(attachedPath).exists())
    }

    @Test
    fun `a cancelled capture leaves nothing in the cache`() = runTest {
        val viewModel = viewModel(ScriptedParser())
        viewModel.onTextChange("what is on the plate")
        // What a camera app may have written before the user backed out of it.
        val partial = File(viewModel.onCaptureRequested().path).apply { writeText("partial capture") }

        viewModel.onCaptureResult(captured = false)

        assertFalse(partial.exists())
        assertNull(viewModel.input.photoPath)
        assertFalse(viewModel.input.photoFailed)
    }

    @Test
    fun `a repeated capture request drops the target that never arrived`() = runTest {
        val viewModel = viewModel(ScriptedParser())
        val abandoned = File(viewModel.onCaptureRequested().path).apply { writeText("stale") }

        viewModel.onCaptureRequested()

        assertFalse(abandoned.exists())
    }

    @Test
    fun `a capture that returns after the screen was recreated is still attached`() = runTest {
        val viewModel = viewModel(ScriptedParser())
        viewModel.onTextChange("what is on the plate")
        val capture = viewModel.onCaptureRequested()
        writeImage(File(capture.path))

        // The composable that launched the camera may be gone; the target is owned here.
        viewModel.onCaptureResult(captured = true)
        runCurrent()

        assertNotNull(viewModel.input.photoPath)
        assertFalse(viewModel.input.photoFailed)
    }

    @Test
    fun `a missing camera app is reported instead of attaching anything`() = runTest {
        val viewModel = viewModel(ScriptedParser())

        viewModel.onCaptureUnavailable()

        assertTrue(viewModel.input.photoFailed)
        assertNull(viewModel.input.photoPath)
        assertFalse(viewModel.input.isAttachingPhoto)
    }

    @Test
    fun `every item is read on its own and one confirmation merges them in order`() = runTest {
        val repository = FakeMealRepository()
        val parser =
            ScriptedParser(
                ParseResult.Success(listOf(foodItem(name = "Omelette")), "first note"),
                ParseResult.Success(listOf(foodItem(name = "Coffee")), "second note"),
            )
        val viewModel = viewModel(parser, repository)
        viewModel.onTextChange("omelette")
        viewModel.onAddInput()
        val second = viewModel.uiState.value.inputs.last().key
        viewModel.onTextChange(second, "coffee with milk")

        viewModel.onParse()
        runCurrent()

        assertEquals<List<UserInput>>(
            listOf(UserInput.Text("omelette"), UserInput.Text("coffee with milk")),
            parser.inputs,
        )
        val state = viewModel.uiState.value
        assertTrue(state.isConfirming)
        assertEquals(listOf("Omelette", "Coffee"), state.items.map { it.name })
        assertEquals("first note\nsecond note", state.note)

        viewModel.onConfirm()
        runCurrent()
        val meal = repository.meals.value.single()
        assertEquals("omelette\ncoffee with milk", meal.rawUserInput)
        assertEquals(EntrySource.LLM_TEXT, meal.source)
    }

    @Test
    fun `a photo belongs to one item and only that request carries it`() = runTest {
        val repository = FakeMealRepository()
        val parser =
            ScriptedParser(
                ParseResult.Success(listOf(foodItem()), null),
                ParseResult.Success(listOf(foodItem()), null),
            )
        val viewModel = viewModel(parser, repository)
        viewModel.onTextChange("omelette")
        viewModel.onAddInput()
        val second = viewModel.uiState.value.inputs.last().key
        viewModel.onTextChange(second, "this is on the plate")
        viewModel.onPhotoPicked(second, sourceImage())
        runCurrent()
        val attachedPath = assertNotNull(viewModel.uiState.value.inputs.last().photoPath)

        viewModel.onParse()
        runCurrent()

        assertEquals(UserInput.Text("omelette"), parser.inputs.first())
        assertEquals(UserInput.TextWithPhoto("this is on the plate", attachedPath), parser.inputs.last())
        // Contract §8: that item's final answer arrived, so its photo is gone.
        assertFalse(File(attachedPath).exists())

        viewModel.onConfirm()
        runCurrent()
        assertEquals(EntrySource.LLM_PHOTO, repository.meals.value.single().source)
    }

    @Test
    fun `a blank item blocks the whole parse`() = runTest {
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem()), null))
        val viewModel = viewModel(parser)
        viewModel.onTextChange("omelette")
        viewModel.onAddInput()

        viewModel.onParse()
        runCurrent()

        assertTrue(viewModel.uiState.value.inputs.last().textMissing)
        assertFalse(viewModel.input.textMissing)
        assertTrue(parser.inputs.isEmpty())
    }

    @Test
    fun `a failed item keeps the read ones and is the only one resent`() = runTest {
        val parser =
            ScriptedParser(
                ParseResult.Success(listOf(foodItem(name = "Omelette")), null),
                ParseResult.Failure(FailureReason.TIMEOUT),
                ParseResult.Success(listOf(foodItem(name = "Coffee")), null),
            )
        val viewModel = viewModel(parser)
        viewModel.onTextChange("omelette")
        viewModel.onAddInput()
        val second = viewModel.uiState.value.inputs.last().key
        viewModel.onTextChange(second, "coffee")
        viewModel.onParse()
        runCurrent()

        assertFalse(viewModel.uiState.value.isConfirming)
        assertTrue(viewModel.input.isParsed)
        assertEquals(FailureReason.TIMEOUT, viewModel.uiState.value.inputs.last().failure)

        viewModel.onRetry(second)
        runCurrent()

        assertEquals(3, parser.inputs.size)
        assertTrue(viewModel.uiState.value.isConfirming)
        assertEquals(listOf("Omelette", "Coffee"), viewModel.uiState.value.items.map { it.name })
    }

    @Test
    fun `removing an item deletes its photo and drops it from the confirmation`() = runTest {
        val parser = ScriptedParser(ParseResult.Success(listOf(foodItem(name = "Omelette")), null))
        val viewModel = viewModel(parser)
        viewModel.onTextChange("omelette")
        viewModel.onAddInput()
        val second = viewModel.uiState.value.inputs.last().key
        viewModel.onTextChange(second, "this is on the plate")
        viewModel.onPhotoPicked(second, sourceImage())
        runCurrent()
        val attachedPath = assertNotNull(viewModel.uiState.value.inputs.last().photoPath)

        viewModel.onRemoveInput(second)
        viewModel.onParse()
        runCurrent()

        assertFalse(File(attachedPath).exists())
        assertEquals<List<UserInput>>(listOf(UserInput.Text("omelette")), parser.inputs)
        assertEquals(listOf("Omelette"), viewModel.uiState.value.items.map { it.name })
    }

    @Test
    fun `the only item cannot be removed`() = runTest {
        val viewModel = viewModel(ScriptedParser())

        viewModel.onRemoveInput(FIRST_ITEM_KEY)

        assertEquals(1, viewModel.uiState.value.inputs.size)
    }

    /** The single item most tests describe, addressed the way the screen addresses it. */
    private val EntryViewModel.input: EntryInputUiState
        get() = uiState.value.inputs.first()

    private fun EntryViewModel.onTextChange(text: String) = onTextChange(FIRST_ITEM_KEY, text)

    private fun EntryViewModel.onPhotoPicked(source: Uri) = onPhotoPicked(FIRST_ITEM_KEY, source)

    private fun EntryViewModel.onRemovePhoto() = onRemovePhoto(FIRST_ITEM_KEY)

    private fun EntryViewModel.onClarificationAnswerChange(answer: String) =
        onClarificationAnswerChange(FIRST_ITEM_KEY, answer)

    private fun EntryViewModel.onSubmitClarification() = onSubmitClarification(FIRST_ITEM_KEY)

    private fun EntryViewModel.onRetry() = onRetry(FIRST_ITEM_KEY)

    private fun EntryViewModel.onCaptureRequested() = onCaptureRequested(FIRST_ITEM_KEY)

    private fun EntryViewModel.onCaptureUnavailable() = onCaptureUnavailable(FIRST_ITEM_KEY)

    private class ScriptedParser(vararg results: ParseResult) : NutritionParser {
        val inputs = mutableListOf<UserInput>()
        private val scripted = results.toMutableList()

        override suspend fun parse(input: UserInput): ParseResult {
            inputs += input
            return scripted.removeFirstOrNull() ?: ParseResult.Failure(FailureReason.UNKNOWN)
        }
    }

    /** A real decodable image, so the store produces a real upload candidate. */
    private fun sourceImage(): Uri = Uri.fromFile(writeImage(File(context.filesDir, "${UUID.randomUUID()}.jpg")))

    private fun writeImage(file: File): File {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
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
            photoStore = photoStore,
        )
    }
}
