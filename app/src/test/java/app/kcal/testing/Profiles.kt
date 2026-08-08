package app.kcal.testing

import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.StoredProfile

/** A complete, valid profile shared by tests that only care about completeness. */
fun completeProfile(
    currentWeightKg: Double? = 82.4,
    heightCm: Double? = 176.0,
    ageYears: Int? = 34,
    energyEquationSex: EnergyEquationSex? = EnergyEquationSex.MALE,
    activityLevel: ActivityLevel? = ActivityLevel.LIGHT,
    targetWeightKg: Double? = 78.0,
    requestedLossRateKgPerWeek: Double? = 0.5,
): StoredProfile = StoredProfile(
    currentWeightKg = currentWeightKg,
    heightCm = heightCm,
    ageYears = ageYears,
    energyEquationSex = energyEquationSex,
    activityLevel = activityLevel,
    targetWeightKg = targetWeightKg,
    requestedLossRateKgPerWeek = requestedLossRateKgPerWeek,
)
