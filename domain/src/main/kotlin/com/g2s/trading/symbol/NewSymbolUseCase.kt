package com.g2s.trading.symbol

import com.g2s.trading.event.NewPositionEvent
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.position.NewPositionUseCase
import com.g2s.trading.strategy.NewStrategySpecUseCase
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class NewSymbolUseCase(
    private val exchangeImpl: Exchange,
    strategySpecUseCase: NewStrategySpecUseCase,
    positionUseCase: NewPositionUseCase
) {
    private val symbols: Map<Symbol, AtomicBoolean>

    init {
        symbols = strategySpecUseCase.findAllServiceSpecs()
            .flatMap { strategySpec -> strategySpec.symbols }
            .toSet().associateWith { AtomicBoolean(false) }

        val positions = positionUseCase.getAllPositions()
        positions.forEach { position ->
            useSymbol(position.openOrder.symbol)
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

    @EventListener
    fun handlePositionClosedEvent(event: NewPositionEvent.PositionClosedEvent) {
        unUseSymbol(event.source.openOrder.symbol)
    }

    fun getAllSymbols(): Set<Symbol> {
        return symbols.keys
    }

    fun getQuantityPrecision(symbol: Symbol): Int {
        return exchangeImpl.getQuantityPrecision(symbol)
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

    fun getCommissionRate(symbol: Symbol): Double {
        return exchangeImpl.getCommissionRate(symbol)
    }
}
