package com.betesepmu.vendor

import com.betesepmu.vendor.bet.PmuPricing
import com.betesepmu.vendor.model.BetTypeOption
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the ported bet-cost formula against the price tables in `betesepmu/constants.ts`
 * and the cost logic in `App.tsx` (updateBetSlip). Keeps the native terminal charging the
 * exact same stakes as the web app.
 */
class PmuPricingTest {

    private fun cost(bt: BetTypeOption, numbers: Int, x: Int) = PmuPricing.costFor(bt, numbers, x)

    @Test
    fun perHorseBets_chargePerSelectedHorse() {
        // Simple Gagnant/Placé: (numbers + X) * 30
        assertEquals(30.0, cost(BetTypeOption.SimpleGagnant, 1, 0), 0.0)
        assertEquals(90.0, cost(BetTypeOption.SimpleGagnant, 3, 0), 0.0)
        assertEquals(120.0, cost(BetTypeOption.SimplePlace, 3, 1), 0.0) // 4 * 30
    }

    @Test
    fun coupleBets_usePriceMapAndXPriceMap() {
        assertEquals(30.0, cost(BetTypeOption.CoupleGagnant, 2, 0), 0.0)
        assertEquals(180.0, cost(BetTypeOption.CoupleGagnant, 4, 0), 0.0)   // priceMap[4]
        assertEquals(3600.0, cost(BetTypeOption.CouplePlace, 16, 0), 0.0)   // priceMap[16]
        assertEquals(450.0, cost(BetTypeOption.CoupleGagnant, 1, 1), 0.0)   // xPriceMap[1][1]
    }

    @Test
    fun tierce_quarte_quinte_matchTables() {
        assertEquals(25.0, cost(BetTypeOption.Tierce, 3, 0), 0.0)
        assertEquals(250.0, cost(BetTypeOption.Tierce, 5, 0), 0.0)          // priceMap[5]
        assertEquals(350.0, cost(BetTypeOption.Tierce, 2, 1), 0.0)          // xPriceMap[1][2]
        assertEquals(5250.0, cost(BetTypeOption.Tierce, 1, 2), 0.0)         // xPriceMap[2][1]
        assertEquals(68250.0, cost(BetTypeOption.Quarte, 1, 3), 0.0)        // xPriceMap[3][1]
        assertEquals(30.0, cost(BetTypeOption.Quinte, 5, 0), 0.0)
        assertEquals(982800.0, cost(BetTypeOption.Quinte, 1, 4), 0.0)       // xPriceMap[4][1]
    }

    @Test
    fun multiBets_matchTables() {
        assertEquals(40.0, cost(BetTypeOption.Multi4, 4, 0), 0.0)
        assertEquals(40.0, cost(BetTypeOption.Multi7, 7, 0), 0.0)
        assertEquals(457600.0, cost(BetTypeOption.Multi7, 16, 0), 0.0)      // priceMap[16]
        assertEquals(68640.0, cost(BetTypeOption.Multi7, 1, 6), 0.0)        // xPriceMap[6][1]
    }

    @Test
    fun unavailableCombination_isZero() {
        // No priceMap entry for a single horse on a Couplé (min is 2).
        assertEquals(0.0, cost(BetTypeOption.CoupleGagnant, 1, 0), 0.0)
    }

    @Test
    fun minHorses_matchConstants() {
        assertEquals(1, PmuPricing.minHorses(BetTypeOption.SimpleGagnant))
        assertEquals(2, PmuPricing.minHorses(BetTypeOption.CoupleGagnant))
        assertEquals(3, PmuPricing.minHorses(BetTypeOption.Tierce))
        assertEquals(5, PmuPricing.minHorses(BetTypeOption.Quinte))
        assertEquals(7, PmuPricing.minHorses(BetTypeOption.Multi7))
    }

    @Test
    fun betTypeLabels_matchWireFormat() {
        // Labels must equal the exact strings stored in Firestore `selections[].betType`.
        assertEquals("Simple Gagnant", BetTypeOption.SimpleGagnant.label)
        assertEquals("Couplé Placé", BetTypeOption.CouplePlace.label)
        assertEquals("Tiercé", BetTypeOption.Tierce.label)
        assertEquals("Quarté+", BetTypeOption.Quarte.label)
        assertEquals(BetTypeOption.Quinte, BetTypeOption.fromLabel("Quinté+"))
    }
}
