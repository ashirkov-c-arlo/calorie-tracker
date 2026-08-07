package app.kcal.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.kcal.R
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode

/** A blocking failure the user can retry, such as unavailable local storage. */
@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.action_retry))
            }
        }
    }
}

@Preview(name = "Error White")
@Composable
private fun ErrorScreenWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        ErrorScreen(message = stringResource(R.string.startup_failed), onRetry = {})
    }
}

@Preview(name = "Error Black")
@Composable
private fun ErrorScreenBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) {
        ErrorScreen(message = stringResource(R.string.startup_failed), onRetry = {})
    }
}
