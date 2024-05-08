package com.g2s.trading.symbol

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.strategy.StrategySpecRepository
import com.g2s.trading.strategy.StrategySpecServiceStatus
import com.g2s.trading.strategy.StrategyType
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class SymbolUseCase(
    val exchangeImpl: Exchange,
    val mongoStrategySpecRepository: StrategySpecRepository
) {

    private val symbols: MutableSet<Symbol> = StrategyType.entries
        .flatMap { strategyType ->
            mongoStrategySpecRepository.findAllServiceStrategySpecByType(strategyType.value)
                .filter { strategySpec -> strategySpec.status == StrategySpecServiceStatus.SERVICE }
                .flatMap { strategySpec -> strategySpec.symbols }
        }.toMutableSet()

    private val symbolLeverageMap = ConcurrentHashMap<Symbol, Int>().apply {
        symbols.forEach { symbol ->
            put(symbol, exchangeImpl.getLeverage(symbol))
        }
    }


    fun getAllSymbols(): List<Symbol> {
        return symbols.toList()
    }

    fun getQuantityPrecision(symbol: Symbol): Int {
        return exchangeImpl.getQuantityPrecision(symbol)
    }

    // 시장가 주문일 때만 적용
    // 시장가 주문이 아닐 때 filterType : LOT_SIZE
    fun getMinQty(symbol: Symbol): Double {
        return exchangeImpl.getMinQty(symbol)
    }

    fun getMinNotionalValue(symbol: Symbol): Double {
        return exchangeImpl.getMinNotionalValue(symbol)
    }

    fun getPricePrecision(symbol: Symbol): Int {
        return exchangeImpl.getPricePrecision(symbol)
    }

    fun getMinPrice(symbol: Symbol): Double {
        return exchangeImpl.getMinPrice(symbol)
    }

    fun getTickSize(symbol: Symbol): Double {
        return exchangeImpl.getTickSize(symbol)
    }

    fun getLeverage(symbol: Symbol): Int {
        return symbolLeverageMap[symbol]!!
    }

    fun setLeverage(symbol: Symbol, leverage: Int): Int {
        val changedLeverage = exchangeImpl.setLeverage(symbol, leverage)
        symbolLeverageMap[symbol] = changedLeverage

        return changedLeverage
    }

    fun getCommissionRate(symbol: Symbol): Double {
        val takerCommissionRate = exchangeImpl.getCommissionRate(symbol)

        return takerCommissionRate
    }
}
