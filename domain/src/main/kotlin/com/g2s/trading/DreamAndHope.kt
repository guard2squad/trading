package com.g2s.trading

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.math.abs

@Service
class DreamAndHope(
    private val exchangeImpl: Exchange
) {

    companion object {
        private const val UNREALIZED_PROFIT = 5
        private const val UNREALIZED_LOSS = 0
        private const val HAMMER_RATIO = 1
        private const val BTC_RATIO = 0.25
        private const val SYMBOL = "BTCUSDT"
        private const val TYPE = "MARKET"
    }
    fun test() : String {
        val indicators = exchangeImpl.getIndicator("BTCUSDT", "1m", 1)
        val account = exchangeImpl.getAccount("USDT", System.currentTimeMillis().toString())
        val position = exchangeImpl.getPosition("BTCUSDT", System.currentTimeMillis().toString())
        if (position.positionAmt != 0.0) {
            if (position.unRealizedProfit > UNREALIZED_PROFIT) {
                exchangeImpl.closePosition(
                    symbol = SYMBOL,
                    side = "SELL",
                    quantity = String.format("%.3f", abs(position.positionAmt)),
                    positionSide = "BOTH", // ONE_WAY_MODE
                    type = TYPE,
                    timestamp = LocalDateTime.now().toString()
                )
            }
            if (position.unRealizedProfit < UNREALIZED_LOSS) {
                exchangeImpl.closePosition(
                    symbol = SYMBOL,
                    side = "SELL",
                    quantity = String.format("%.3f", abs(position.positionAmt)),
                    positionSide = "BOTH", // ONE_WAY_MODE
                    type = TYPE,
                    timestamp = LocalDateTime.now().toString()
                )
            }
        }
        else {
            if (account.availableBalance > account.balance * BTC_RATIO) {
                val side: String = if (indicators.open < indicators.close) {
                    "BUY"
                } else {
                    "SELL"
                }
                if ((indicators.high - indicators.low) / (indicators.close - indicators.open) > HAMMER_RATIO) {
                    exchangeImpl.openPosition(
                        symbol = SYMBOL,
                        side = side,
                        quantity = String.format("%.3f", account.availableBalance / indicators.latestPrice * BTC_RATIO),
                        positionSide = "BOTH", // ONE_WAY_MODE
                        type = TYPE,
                        timestamp = LocalDateTime.now().toString()
                    )
                }
            }
        }

        return "Not yet implemented"
    }


    @PostConstruct
    fun schedule() {
    }
}