package com.g2s.trading.symbol

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.StrategySpecUseCase
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class SymbolUseCase(
    private val exchangeImpl: Exchange,
    strategySpecUseCase: StrategySpecUseCase,
    positionUseCase: PositionUseCase
) {
    private val symbols: Map<Symbol, AtomicBoolean>

    init {
        symbols = strategySpecUseCase.findAllServiceSpecs()
            .flatMap { strategySpec -> strategySpec.symbols }.map { symbolValue -> generateSymbol(symbolValue) }
            .toSet().associateWith { AtomicBoolean(false) }

        val positions = positionUseCase.getAllPositions()
        positions.forEach { position ->
            useSymbol(position.symbol)
        }
    }


    fun useSymbol(symbol: Symbol): Boolean {
        return symbols[symbol]?.let {
            it.compareAndSet(false, true)
        } ?: false
    }

    fun unUseSymbol(symbol: Symbol) {
        symbols[symbol]!!.set(false)
    }

    fun getSymbol(value: String): Symbol? {
        return symbols.keys.firstOrNull { symbol -> symbol.value == value }
    }

    fun getAllSymbols(): Set<Symbol> {
        return symbols.keys
    }

    private fun generateSymbol(symbolValue: String): Symbol {
        return Symbol(
            value = symbolValue,
            quantityPrecision = exchangeImpl.getQuantityPrecision(symbolValue),
            pricePrecision = exchangeImpl.getPricePrecision(symbolValue),
            minimumNotionalValue = exchangeImpl.getMinNotionalValue(symbolValue),
            minimumPrice = exchangeImpl.getMinPrice(symbolValue),
            tickSize = exchangeImpl.getTickSize(symbolValue),
            commissionRate = exchangeImpl.getCommissionRate(symbolValue),
        )
    }
}
