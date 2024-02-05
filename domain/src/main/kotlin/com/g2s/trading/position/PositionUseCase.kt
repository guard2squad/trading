package com.g2s.trading.position

import com.g2s.trading.exchange.Exchange
import com.g2s.trading.order.Order
import com.g2s.trading.order.Symbol
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PositionUseCase(
    private val exchangeImpl: Exchange
) {
    private val strategyPositionMap = mutableMapOf<String, Position>()
    private val lastPositions = mutableMapOf<CompositeKey, Position>()

    fun openPosition(order: Order): Position {
        return exchangeImpl.openPosition(order)
    }

    fun closePosition(position: Position) {
        exchangeImpl.closePosition(position)
    }

    fun hasPosition(strategyKey: String): Boolean {
        return strategyPositionMap[strategyKey] != null
    }

    fun getAllUsedSymbols(): Set<Symbol> {
        return strategyPositionMap.values.map { it.symbol }.toSet()
    }

    fun addPosition(strategyKey: String, position: Position) {
        strategyPositionMap[strategyKey] = position
    }

    fun removePosition(strategyKey: String) {
        strategyPositionMap.remove(strategyKey)
    }

    fun getPosition(strategyKey: String): Position? {
        return strategyPositionMap[strategyKey]
    }

    fun updateLastPosition(strategyKey: String, position: Position) {
        lastPositions[CompositeKey(strategyKey, position.symbol)] = position
    }

    fun findLastPosition(strategyKey: String, symbol: Symbol): Position? {
        return lastPositions[CompositeKey(strategyKey, symbol)]
    }

    internal data class CompositeKey(
        val strategyKey: String,
        val symbol: Symbol
    )
}
