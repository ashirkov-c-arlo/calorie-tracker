package app.kcal.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.kcal.R
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = label })
        }
    }
}

/**
 * Stage 1 stand-in for a screen body. Replaced by real content in later stages.
 */
@Composable
fun PlaceholderBody(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(KcalSpacing.medium),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(name = "Loading White")
@Composable
private fun LoadingScreenWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        LoadingScreen()
    }
}

@Preview(name = "Loading Black")
@Composable
private fun LoadingScreenBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) {
        LoadingScreen()
    }
}
