package app.kcal.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back_content_description),
                        )
                    }
                },
            )
            PlaceholderBody(text = stringResource(R.string.placeholder_settings))
        }
    }
}

@Preview(name = "Settings White")
@Composable
private fun SettingsScreenWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        SettingsScreen(onBackClick = {})
    }
}

@Preview(name = "Settings Black")
@Composable
private fun SettingsScreenBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) {
        SettingsScreen(onBackClick = {})
    }
}
