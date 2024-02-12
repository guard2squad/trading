package com.g2s.trading.position

import com.g2s.trading.EventUseCase
import com.g2s.trading.PositionEvent
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.order.Symbol
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

@Service
class PositionUseCase(
    private val exchangeImpl: Exchange,
    private val accountUseCase: AccountUseCase,
    private val eventUseCase: EventUseCase,
    private val positionRepository: PositionRepository
) {
    private val strategyPositionMap = ConcurrentHashMap<String, Position>()

    init {
        loadPositions()
    }

    private fun loadPositions() {
        // db 조회
        val positions = positionRepository.findAllPositions()
        // map 저장
        positions.forEach { position ->
            strategyPositionMap[position.strategyKey] = position
        }
    }

    fun openPosition(position: Position) {
        strategyPositionMap.computeIfAbsent(position.strategyKey) { _ ->
            // send order
            exchangeImpl.openPosition(position)
            // set account unsynced
            accountUseCase.setUnSynced()
            // publish event
            eventUseCase.publishEvent(PositionEvent.PositionOpenEvent(position))
            // save map
            position
        }
    }

    @EventListener
    fun handlePositionRefreshEvent(event: PositionEvent.PositionRefreshEvent) {
        event.source.forEach { positionRefreshData ->
            val strategyKey = strategyPositionMap.entries.stream().filter { entry ->
                entry.value.symbol == positionRefreshData.symbol
            }.findFirst().map { it.key }.getOrNull()

            if (strategyKey != null) {
                strategyPositionMap.computeIfPresent(strategyKey) { _, old ->
                    Position.update(old, positionRefreshData)
                }
            }
        }
    }

    fun closePosition(position: Position) {
        exchangeImpl.closePosition(position)
        accountUseCase.setUnSynced()
        strategyPositionMap.remove(position.strategyKey)
    }

    fun hasPosition(strategyKey: String): Boolean {
        return strategyPositionMap[strategyKey] != null
    }

    fun getAllUsedSymbols(): Set<Symbol> {
        return strategyPositionMap.values.map { it.symbol }.toSet()
    }

    fun getPosition(strategyKey: String): Position? {
        return strategyPositionMap[strategyKey]
    }
}
