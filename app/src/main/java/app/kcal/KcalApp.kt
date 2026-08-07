package app.kcal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kcal.core.ui.ErrorScreen
import app.kcal.core.ui.LoadingScreen
import app.kcal.feature.profile.ProfileSetupRoute
import app.kcal.navigation.KcalNavHost

/**
 * The main navigation stays closed until startup finished and the required profile data
 * exists.
 */
@Composable
fun KcalApp(uiState: MainUiState, modifier: Modifier = Modifier, onRetryStartup: () -> Unit = {}) {
    when {
        uiState.startupFailed ->
            ErrorScreen(
                message = stringResource(R.string.startup_failed),
                onRetry = onRetryStartup,
                modifier = modifier,
            )

        uiState.isLoading -> LoadingScreen(modifier = modifier)

        !uiState.isProfileComplete -> ProfileSetupRoute()

        else -> KcalNavHost(modifier = modifier)
    }
}
