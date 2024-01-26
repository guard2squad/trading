package com.g2s.trading.position

import com.g2s.trading.Exchange
import com.g2s.trading.order.Order
import com.g2s.trading.order.Symbol
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PositionUseCase(
    private val exchangeImpl: Exchange
) {
    val strategyPositionMap: MutableMap<String, Position> = ConcurrentHashMap()

    fun openPosition(order: Order): Position {

    }

    fun closePosition(position: Position) {

    }


    fun hasPosition(strategyKey: String): Boolean {
        return strategyPositionMap.containsKey(strategyKey)
    }

    fun getAllUsedSymbols(): Set<Symbol> {
        return strategyPositionMap.values.map { it.symbol }.toSet()
    }

    fun registerPosition(strategyKey: String ,position: Position) {
        strategyPositionMap[strategyKey] = position
    }
}
