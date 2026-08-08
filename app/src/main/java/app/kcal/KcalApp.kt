package app.kcal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kcal.core.ui.LoadingScreen
import app.kcal.feature.profile.ProfileSetupRoute
import app.kcal.navigation.KcalNavHost

/**
 * The main navigation stays closed until the required profile data exists.
 */
@Composable
fun KcalApp(uiState: MainUiState, modifier: Modifier = Modifier) {
    when {
        uiState.isLoading -> LoadingScreen(modifier = modifier)
        !uiState.isProfileComplete -> ProfileSetupRoute()
        else -> KcalNavHost(modifier = modifier)
    }
}
