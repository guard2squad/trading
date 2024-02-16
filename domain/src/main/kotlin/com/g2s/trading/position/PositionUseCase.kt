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
        // publish event
        eventUseCase.publishEvent(PositionEvent.PositionsLoadEvent(positions))
    }

    fun openPosition(position: Position) {
        strategyPositionMap.computeIfAbsent(position.strategyKey) { _ ->
            // send order
            exchangeImpl.openPosition(position)
            // get opened position
            val opened = exchangeImpl.getPosition(position.symbol)
            strategyPositionMap[position.strategyKey] = opened
            // set account unsynced
            accountUseCase.setUnSynced()
            // publish event
            eventUseCase.publishEvent(PositionEvent.PositionOpenEvent(opened))
            // update DB
            positionRepository.savePosition(opened)
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
                    val updated = Position.update(old, positionRefreshData)
                    // update DB
                    positionRepository.savePosition(updated)
                    updated
                }
            }
        }
    }

    fun closePosition(position: Position) {
        exchangeImpl.closePosition(position)
        accountUseCase.setUnSynced()
        strategyPositionMap.remove(position.strategyKey)
        // DB update
        positionRepository.deletePosition(position)
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
