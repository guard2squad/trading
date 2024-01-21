package com.g2s.trading.strategy

import com.g2s.trading.Indicator
import com.g2s.trading.Order
import com.g2s.trading.OrderSide
import com.g2s.trading.OrderType
import com.g2s.trading.Symbol
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.account.Position
import com.g2s.trading.indicator.IndicatorUseCase
import com.g2s.trading.indicator.indicator.Interval
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class SimpleStrategy(
    private val accountUseCase: AccountUseCase,
    private val indicatorUseCase: IndicatorUseCase
) : Strategy {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val symbol = "BTCUSDT"
    private val hammerRatio = 1.0

    override fun invoke() {
        val symbols = listOf<Symbol>(Symbol.BTCUSDT) // TODO get symbol
        val positions = accountUseCase.getPositions(symbols)
        val lastPrices: Map<Symbol, Double> = symbols.map { it to indicatorUseCase.getLastPrice(it) }.toMap()


        logger.debug("closing positions...")
        val closingPositions = positions.filter { shouldClose(it, lastPrices[it.symbol]!! ) }
        accountUseCase.closePositions(closingPositions)
        logger.debug("closed positions... ${closingPositions.joinToString(",")}")

        symbols.forEach { symbol ->

            val candleSticks = indicatorUseCase.getCandleStick(symbol, Interval.ONE_MINUTE, 1)
            val lastCandleStick = candleSticks.last()
        }
    }

    fun shouldOpen(indicator: Indicator): Boolean {
        return (indicator.high - indicator.low) / (indicator.close - indicator.open) > hammerRatio
    }

    fun shouldClose(position: Position, lastPrice: Double): Boolean {
        val decimalLastPrice = BigDecimal(lastPrice)
            val pnlPercentage = decimalLastPrice.multiply(BigDecimal(100)).divide(BigDecimal(position.entryPrice))

            if(pnlPercentage > ? )

        return true
    }

    fun orderSide(indicator: Indicator): OrderSide {
        return if (indicator.open < indicator.close) {
            OrderSide.BUY
        } else {
            OrderSide.SELL
        }
    }

    fun makeOrder(symbol: Symbol, orderType: OrderType, orderSide: OrderSide, quantity: Double): Order {
        return Order(
            symbol = symbol,
            orderType = orderType,
            orderSide = orderSide,
            quantity = String.format("%.3f", quantity),
        )
    }
}
