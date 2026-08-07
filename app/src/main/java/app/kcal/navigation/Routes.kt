package app.kcal.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations. The stateful composables that resolve view models are
 * named `...Route`, so destinations use the `...Destination` suffix.
 */
@Serializable
data object TodayDestination

@Serializable
data object TrendsDestination

@Serializable
data object HistoryDestination

@Serializable
data object SettingsDestination
