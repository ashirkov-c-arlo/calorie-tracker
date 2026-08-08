package app.kcal.domain.model

import java.math.BigDecimal

/** Exact journal totals. Wider types keep valid reviewed items from overflowing the day. */
data class MacroTotals(val kcal: Long, val proteinG: BigDecimal, val fatG: BigDecimal, val carbsG: BigDecimal) {
    companion object {
        val ZERO = MacroTotals(
            kcal = 0L,
            proteinG = BigDecimal.ZERO,
            fatG = BigDecimal.ZERO,
            carbsG = BigDecimal.ZERO,
        )

        fun from(macros: Macros): MacroTotals = MacroTotals(
            kcal = macros.kcal.toLong(),
            proteinG = BigDecimal.valueOf(macros.proteinG),
            fatG = BigDecimal.valueOf(macros.fatG),
            carbsG = BigDecimal.valueOf(macros.carbsG),
        )
    }
}
