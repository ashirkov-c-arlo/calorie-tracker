package app.kcal.domain.usecase

import app.kcal.domain.model.Macros
import app.kcal.testing.foodItem
import app.kcal.testing.mealEntry
import org.junit.Test
import kotlin.test.assertEquals

class AggregateMealMacrosTest {

    private val aggregate = AggregateMealMacros()

    @Test
    fun `aggregates one meal with multiple items`() {
        val meal =
            mealEntry(
                items =
                listOf(
                    foodItem(kcal = 300, proteinG = 10.0, fatG = 5.0, carbsG = 50.0),
                    foodItem(kcal = 120, proteinG = 2.5, fatG = 1.0, carbsG = 24.0),
                ),
            )

        assertEquals(
            Macros(kcal = 420, proteinG = 12.5, fatG = 6.0, carbsG = 74.0),
            aggregate(listOf(meal)),
        )
    }

    @Test
    fun `aggregates multiple meals and returns zero for an empty day`() {
        val meals =
            listOf(
                mealEntry(id = 1, items = listOf(foodItem(kcal = 250, proteinG = 20.0))),
                mealEntry(id = 2, items = listOf(foodItem(kcal = 400, proteinG = 30.0))),
            )

        assertEquals(650, aggregate(meals).kcal)
        assertEquals(50.0, aggregate(meals).proteinG)
        assertEquals(Macros.ZERO, aggregate(emptyList()))
    }
}
