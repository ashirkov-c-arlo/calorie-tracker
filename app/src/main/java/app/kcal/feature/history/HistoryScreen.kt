package app.kcal.feature.history

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            TopAppBar(title = { Text(text = stringResource(R.string.history_title)) })
            PlaceholderBody(text = stringResource(R.string.placeholder_history))
        }
    }
}

@Preview(name = "History White")
@Composable
private fun HistoryScreenWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        HistoryScreen()
    }
}

@Preview(name = "History Black")
@Composable
private fun HistoryScreenBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) {
        HistoryScreen()
    }
}
