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
            takeProfitPrice = position.referenceData["takeProfitPrice"].asDouble(),
            stopLossPrice = position.referenceData["stopLossPrice"].asDouble(),
            closePrice = position.closePrice,
            expectedFee =   // (예상 진입가 + 예상 이익 가격) * 예상 수량(==채결 수량) * 수수료율
            ((BigDecimal(position.expectedEntryPrice) + BigDecimal(position.referenceData["takeProfitPrice"].asDouble()))
                    * BigDecimal(position.expectedQuantity)
                    * BigDecimal(position.symbol.commissionRate)).toDouble(),
            fee = position.fee,
            pnl = position.pnl,
            closeTime = position.closeTime,
            referenceData = position.referenceData
        )
        tradingHistoryRepository.saveTradeHistory(history)
    }
}