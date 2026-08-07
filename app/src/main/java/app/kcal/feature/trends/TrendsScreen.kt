package app.kcal.feature.trends

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
fun TrendsScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            TopAppBar(title = { Text(text = stringResource(R.string.trends_title)) })
            PlaceholderBody(text = stringResource(R.string.placeholder_trends))
        }
    }
}

@Preview(name = "Trends White")
@Composable
private fun TrendsScreenWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        TrendsScreen()
    }
}

@Preview(name = "Trends Black")
@Composable
private fun TrendsScreenBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) {
        TrendsScreen()
    }
}
