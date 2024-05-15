package com.g2s.trading.strategy.singlecandle

import com.g2s.trading.order.OrderSide
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode

class SimpleOpenManTest {

    companion object {
        const val BCHUSDT_MINNOTIONAL_VALUE = 20
        const val BCHUSDT_QUANTITY_PRECISION = 3
    }

    @Test
    fun testCalculateQuantity() {
        val res = quantity(
            BigDecimal(BCHUSDT_MINNOTIONAL_VALUE),
            BigDecimal(513.62),
            1.8,
            1.5,
            0.5500000000000682,
            BCHUSDT_QUANTITY_PRECISION,
            OrderSide.SHORT
        )
        println(res)
    }

    private fun quantity(
        minNotional: BigDecimal,
        markPrice: BigDecimal,
        takeProfitFactor: Double,
        stopLossFactor: Double,
        tailLength: Double,
        quantityPrecision: Int,
        orderSide: OrderSide
    ): Double {
        // "code":-4164,"msg":"Order's notional must be no smaller than 100 (unless you choose reduce only)."
        // 수량이 부족하다는 이유로 예외가 너무 자주 떠서 올림으로 처리함
        val quantity = minNotional.divide(markPrice, quantityPrecision, RoundingMode.CEILING).toDouble()
        var takeProfitQuantity: Double
        var stopLossQuantity: Double
        when (orderSide) {
            OrderSide.LONG -> {
                takeProfitQuantity = minNotional.divide(
                    markPrice + (BigDecimal(tailLength) * BigDecimal(takeProfitFactor)),
                    quantityPrecision,
                    RoundingMode.CEILING
                ).toDouble()

                stopLossQuantity = minNotional.divide(
                    markPrice - (BigDecimal(tailLength) * BigDecimal(stopLossFactor)),
                    quantityPrecision,
                    RoundingMode.CEILING
                ).toDouble()
            }

            OrderSide.SHORT -> {
                takeProfitQuantity = minNotional.divide(
                    markPrice - (BigDecimal(tailLength) * BigDecimal(takeProfitFactor)),
                    quantityPrecision,
                    RoundingMode.CEILING
                ).toDouble()

                stopLossQuantity = minNotional.divide(
                    markPrice + (BigDecimal(tailLength) * BigDecimal(stopLossFactor)),
                    quantityPrecision,
                    RoundingMode.CEILING
                ).toDouble()
            }
        }
        return maxOf(takeProfitQuantity, stopLossQuantity, quantity)
    }
}