package com.g2s.trading.position

import com.g2s.trading.Exchange
import com.g2s.trading.Symbol
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PositionUseCase(
    private val exchangeImpl: Exchange,
) {

    val strategyPositionMap: MutableMap<Symbol, Position> = ConcurrentHashMap()

    fun checkPositionBySymbol(symbol: Symbol): Boolean {
        return strategyPositionMap.containsKey(symbol)
    }

    fun registerPosition(position: Position) {
        strategyPositionMap[position.symbol] = position
    }

    fun getPosition(symbol: Symbol): Position? {
        return getAllPositions().firstOrNull { it.symbol == symbol }
    }

    fun getPositions(symbols: List<Symbol>): List<Position> {
        return getAllPositions().filter { symbols.contains(it.symbol) }
    }

    fun getAllPositions(): List<Position> {
        val positions = exchangeImpl.getAllPositions()
        return positions
    }

}
