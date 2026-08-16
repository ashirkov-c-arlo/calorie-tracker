package app.kcal.domain.model

/**
 * Which position of the offered deficit range the user picked: its low bound, its midpoint or
 * its high bound. The percentage and the resulting weekly loss are derived from
 * [DeficitBand], so a stored position stays valid when the body-mass band changes.
 */
enum class LossPace {
    SLOW,
    MODERATE,
    FAST,
}

/**
 * The estimated weekly weight loss for each offered position, in kilograms per week. It is a
 * reference value derived from the calculated deficit, and it is null while the profile cannot
 * produce an energy estimate yet, so no rate is ever fabricated.
 */
data class LossPaceOptions(val slowKgPerWeek: Double?, val moderateKgPerWeek: Double?, val fastKgPerWeek: Double?) {
    fun rateFor(pace: LossPace): Double? = when (pace) {
        LossPace.SLOW -> slowKgPerWeek
        LossPace.MODERATE -> moderateKgPerWeek
        LossPace.FAST -> fastKgPerWeek
    }
}
