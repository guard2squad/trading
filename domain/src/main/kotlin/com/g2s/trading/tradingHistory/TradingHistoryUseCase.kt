package com.g2s.trading.tradingHistory

import com.g2s.trading.position.Position
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class TradingHistoryUseCase(
    private val tradingHistoryRepository: TradingHistoryRepository,
) {
    fun saveHistory(position: Position) {
        val history = TradingHistory(
            symbol = position.symbol.value,
            side = position.side,
            quantity = position.expectedQuantity,
            strategyKey = position.referenceData["strategyKey"].toString(),
            candlestickPattern = position.referenceData["candleStickPattern"].toString(),
            expectedEntryPrice = position.expectedEntryPrice,
            entryPrice = position.price,
            takeProfitPrice = position.takeProfitPrice,
            stopLossPrice = position.stopLossPrice,
            closePrice = position.closePrice,
            expectedFee = (BigDecimal(position.expectedEntryPrice)
                    * BigDecimal(position.expectedQuantity)
                    * BigDecimal(position.symbol.commissionRate)
                    * BigDecimal(2)).toDouble(),
            fee = position.fee,
            pnl = position.pnl,
        )
        tradingHistoryRepository.saveTradeHistory(history)
    }
}