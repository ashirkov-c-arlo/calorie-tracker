package app.kcal.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.kcal.R
import app.kcal.core.designsystem.KcalTheme
import app.kcal.core.ui.PlaceholderBody
import app.kcal.domain.model.ThemeMode

/**
 * The required first-run form. Stage 1 renders the gate only; the fields arrive in stage 2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            TopAppBar(title = { Text(text = stringResource(R.string.profile_setup_title)) })
            PlaceholderBody(text = stringResource(R.string.placeholder_profile_setup))
        }
    }
}

@Preview(name = "Profile setup White")
@Composable
private fun ProfileSetupScreenWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        ProfileSetupScreen()
    }
}

@Preview(name = "Profile setup Black")
@Composable
private fun ProfileSetupScreenBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) {
        ProfileSetupScreen()
    }
}
