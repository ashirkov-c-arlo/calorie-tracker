package app.kcal.di

import app.kcal.core.common.TimeProvider
import app.kcal.domain.repository.DailyTargetRepository
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.ApplyTodayTarget
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.ReconcileTodayTarget
import app.kcal.domain.usecase.SaveProfile
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
    fun provideApplyTodayTarget(
        dailyTargetRepository: DailyTargetRepository,
        calculateDailyTargets: CalculateDailyTargets,
        timeProvider: TimeProvider,
    ): ApplyTodayTarget = ApplyTodayTarget(dailyTargetRepository, calculateDailyTargets, timeProvider)

    @Provides
    fun provideSaveProfile(profileRepository: ProfileRepository, applyTodayTarget: ApplyTodayTarget): SaveProfile =
        SaveProfile(profileRepository, applyTodayTarget)

    @Provides
    fun provideReconcileTodayTarget(
        profileRepository: ProfileRepository,
        applyTodayTarget: ApplyTodayTarget,
    ): ReconcileTodayTarget = ReconcileTodayTarget(profileRepository, applyTodayTarget)
}
