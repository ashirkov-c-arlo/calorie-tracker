package app.kcal.di

import app.kcal.core.common.TimeProvider
import app.kcal.domain.repository.DailyTargetRepository
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.ApplyTodayTarget
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.SaveProfile
import app.kcal.domain.usecase.SuggestLossPaces
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Wiring for the domain layer. The use cases themselves carry no DI annotations, so
 * `domain` stays free of anything but Kotlin and kotlinx.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    fun provideCalculateDailyTargets(): CalculateDailyTargets = CalculateDailyTargets()

    @Provides
    fun provideSuggestLossPaces(calculateDailyTargets: CalculateDailyTargets): SuggestLossPaces =
        SuggestLossPaces(calculateDailyTargets)

    @Provides
    fun provideApplyTodayTarget(
        dailyTargetRepository: DailyTargetRepository,
        calculateDailyTargets: CalculateDailyTargets,
    ): ApplyTodayTarget = ApplyTodayTarget(dailyTargetRepository, calculateDailyTargets)

    @Provides
    fun provideSaveProfile(
        profileRepository: ProfileRepository,
        applyTodayTarget: ApplyTodayTarget,
        timeProvider: TimeProvider,
    ): SaveProfile = SaveProfile(profileRepository, applyTodayTarget, timeProvider)
}
