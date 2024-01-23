package com.g2s.trading.position

import com.g2s.trading.Exchange
import com.g2s.trading.Symbol
import com.g2s.trading.account.AccountUseCase
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PositionUseCase(
    private val exchangeImpl: Exchange,
    private val accountUseCase: AccountUseCase
) {

    val strategyPositionMap: MutableMap<Symbol, Position> = ConcurrentHashMap()

    fun checkPositionBySymbol(symbol: Symbol): Boolean {
        return strategyPositionMap.containsKey(symbol)
    }

    fun registerPosition(position: Position) {
        strategyPositionMap[position.symbol] = position
    }

    fun createPosition(LiquidationData: LiquidationData): Position {
        return Position(
            symbol = Symbol.BTCUSDT,
            liquidationData = LiquidationData
        )
    }

    fun getPosition(symbol: Symbol): Position? {
        return getAllPositions().firstOrNull { it.symbol == symbol }
    }

    fun getPositions(symbols: List<Symbol>): List<Position> {
        return getAllPositions().filter { symbols.contains(it.symbol) }
    }

    fun getAllPositions(): List<Position> {
        val account = accountUseCase.getAccount()
        return account.positions
    }

    // TODO(주문 넣을 때 close 주문 넣도록 바꿀 예정이라 지워도 됨)
    fun closePosition(position: Position) {
        exchangeImpl.closePosition(position)
    }

    fun closePositions(positions: List<Position>) {
        positions.forEach { closePosition(it) }
    }

}
