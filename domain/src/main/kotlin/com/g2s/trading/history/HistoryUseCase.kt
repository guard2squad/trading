package com.g2s.trading.history

import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.symbol.Symbol
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class HistoryUseCase(
    val historyRepository: HistoryRepository
) {

    private val specStage : ConcurrentHashMap<Symbol, StrategySpec> = ConcurrentHashMap()

    fun recordHistory(history: History) {
        assert(specStage.contains(history.symbol))
        val specAppendedHistory = history.copy(
            strategySpec = specStage[history.symbol]
        )
        historyRepository.saveHistory(specAppendedHistory)

        specStage.remove(history.symbol)
    }

    fun stagingSpec(symbol: Symbol, strategySpec: StrategySpec) {
        assert(!specStage.contains(symbol))
        specStage[symbol] = strategySpec
    }

}
