package app.kcal.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
fun TodayScreen(onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = { Text(text = stringResource(R.string.today_title)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_open_content_description),
                        )
                    }
                },
            )
            PlaceholderBody(text = stringResource(R.string.placeholder_today))
        }
    }
}

@Preview(name = "Today White")
@Composable
private fun TodayScreenWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        TodayScreen(onSettingsClick = {})
    }
}

@Preview(name = "Today Black")
@Composable
private fun TodayScreenBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) {
        TodayScreen(onSettingsClick = {})
    }
}
