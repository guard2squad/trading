package com.g2s.trading.position

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.order.OrderSide
import com.g2s.trading.symbol.Symbol
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

data class Position(
    val positionId: String = UUID.randomUUID().toString(),
    val symbol: Symbol,
    val side: OrderSide,
    val referenceData: JsonNode,
    val expectedEntryPrice: Double,
    var expectedQuantity: Double, // 주문한 총 수량
    val openOrderId: String,
    val closeOrderIds: MutableSet<String>
) {
    var price: Double = 0.0
    var quantity: Double = 0.0
    var closePrice: Double = 0.0
    var fee: Double = 0.0
    var pnl: Double = 0.0
    var takeProfitPrice: Double = 0.0
    var stopLossPrice: Double = 0.0

    fun setClosePrice(lastFilledPrice: Double, lastFilledAmount: Double) {
        val decimalAccumulatedAmount = BigDecimal(this.expectedQuantity) - BigDecimal(this.quantity)
        val decimalLastFilledAmount = BigDecimal(lastFilledAmount)
        val decimalClosePrice = BigDecimal(this.closePrice)
        val decimalLastFilledPrice = BigDecimal(lastFilledPrice)
        // (누적 close 채결 수량 * closePrice + 이번에 채결된 수량 * 이번에 채결된 가격) / 누적 close 채결 수량 + 이번에 채결된 수량
        this.closePrice =
            (decimalAccumulatedAmount.multiply(decimalClosePrice)
                .plus(decimalLastFilledAmount.multiply(decimalLastFilledPrice)))
                .divide(    //
                    decimalAccumulatedAmount + decimalLastFilledAmount,
                    2,
                    RoundingMode.HALF_UP
                ).toDouble()
    }
}
