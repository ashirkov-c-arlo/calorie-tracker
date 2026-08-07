package app.kcal.domain.repository

import app.kcal.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Profile state the app shell needs. Stage 2 adds the write operations used by the
 * required first-run form and Settings.
 */
interface ProfileRepository {

    /**
     * True only when every required calculator input is stored and a current weight
     * exists. There is no separate onboarding flag.
     */
    val isProfileComplete: Flow<Boolean>

    val themeMode: Flow<ThemeMode>
}
