package app.kcal.di

import app.kcal.core.common.TimeProvider
import app.kcal.domain.repository.DailyTargetRepository
import app.kcal.domain.repository.MealRepository
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.AggregateMealMacros
import app.kcal.domain.usecase.ApplyTodayTarget
import app.kcal.domain.usecase.BuildHistory
import app.kcal.domain.usecase.BuildWeightTrend
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.SaveMeal
import app.kcal.domain.usecase.SaveProfile
import app.kcal.domain.usecase.SuggestLossPaces
import app.kcal.domain.usecase.ValidateMeal
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
    fun provideSuggestLossPaces(): SuggestLossPaces = SuggestLossPaces()

    @Provides
    fun provideApplyTodayTarget(
        dailyTargetRepository: DailyTargetRepository,
        calculateDailyTargets: CalculateDailyTargets,
    ): ApplyTodayTarget = ApplyTodayTarget(dailyTargetRepository, calculateDailyTargets)

    @Provides
    fun provideValidateMeal(): ValidateMeal = ValidateMeal()

    @Provides
    fun provideAggregateMealMacros(): AggregateMealMacros = AggregateMealMacros()

    @Provides
    fun provideBuildHistory(aggregateMealMacros: AggregateMealMacros): BuildHistory = BuildHistory(aggregateMealMacros)

    @Provides
    fun provideBuildWeightTrend(): BuildWeightTrend = BuildWeightTrend()

    @Provides
    fun provideSaveProfile(
        profileRepository: ProfileRepository,
        calculateDailyTargets: CalculateDailyTargets,
        timeProvider: TimeProvider,
    ): SaveProfile = SaveProfile(profileRepository, calculateDailyTargets, timeProvider)

    @Provides
    fun provideSaveMeal(
        mealRepository: MealRepository,
        profileRepository: ProfileRepository,
        calculateDailyTargets: CalculateDailyTargets,
        validateMeal: ValidateMeal,
        timeProvider: TimeProvider,
    ): SaveMeal = SaveMeal(
        mealRepository = mealRepository,
        profileRepository = profileRepository,
        calculateDailyTargets = calculateDailyTargets,
        validateMeal = validateMeal,
        timeProvider = timeProvider,
    )
}
