package app.kcal

import app.kcal.domain.model.AppLanguage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A cold start must not clear a stored interface language before preferences are read;
 * otherwise the app-locale API recreates the activity twice and the language flickers.
 */
class AppLanguageTest {

    @Test
    fun `nothing is applied while preferences are still loading`() {
        assertNull(MainUiState(isLoading = true, appLanguage = AppLanguage.SYSTEM).appLanguageToApply())
        assertNull(MainUiState(isLoading = true, appLanguage = AppLanguage.RUSSIAN).appLanguageToApply())
    }

    @Test
    fun `the stored language is applied once preferences are loaded`() {
        assertEquals(
            AppLanguage.RUSSIAN,
            MainUiState(isLoading = false, appLanguage = AppLanguage.RUSSIAN).appLanguageToApply(),
        )
        assertEquals(
            AppLanguage.SYSTEM,
            MainUiState(isLoading = false, appLanguage = AppLanguage.SYSTEM).appLanguageToApply(),
        )
    }

    @Test
    fun `a cold start with a stored russian language applies it exactly once`() {
        // The initial state carries the SYSTEM default; applying it would clear the stored
        // locale, so it must stay unapplied until the real value arrives.
        val coldStart = MainUiState()
        val loaded = coldStart.copy(isLoading = false, appLanguage = AppLanguage.RUSSIAN)

        assertNull(coldStart.appLanguageToApply())
        assertEquals(AppLanguage.RUSSIAN, loaded.appLanguageToApply())
    }
}
