package com.g2s.trading.position

import com.g2s.trading.EventUseCase
import com.g2s.trading.PositionEvent
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.order.Symbol
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PositionUseCase(
    private val exchangeImpl: Exchange,
    private val accountUseCase: AccountUseCase,
    private val eventUseCase: EventUseCase,
    private val positionRepository: PositionRepository
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val strategyPositionMap = ConcurrentHashMap<String, Position>()

    init {
        loadPositions()
    }

    private fun loadPositions() {
        // db 조회
        val positions = positionRepository.findAllPositions()
        logger.debug("load positions: ${positions.size}")
        // map 저장
        positions.forEach { position ->
            strategyPositionMap[position.strategyKey] = position
        }
    }

    fun openPosition(position: Position) {
        strategyPositionMap.computeIfAbsent(position.strategyKey) { _ ->
            // set account unsynced
            accountUseCase.setUnSynced()
            // update unsynced position to DB
            positionRepository.savePosition(position)
            // send order
            exchangeImpl.openPosition(position)
            // save unsynced position to map
            position
        }
    }

    fun refreshPosition(positionRefreshData: PositionRefreshData) {
        strategyPositionMap.values.find { position ->
            position.symbol == positionRefreshData.symbol
        }?.let { old ->
            val updated = Position.update(old, positionRefreshData)
            // closed position
            if (updated.positionAmt == 0.0) {
                logger.debug("position is closed because positionAmt is ${updated.positionAmt}")
                positionRepository.deletePosition(updated)
                logger.debug("before closed position delete from strategyPositionMap, it's size is : ${strategyPositionMap.size}")
                strategyPositionMap.remove(updated.strategyKey)
                logger.debug("after closed position delete from strategyPositionMap, it's size is : ${strategyPositionMap.size}")
            }
            // opened position
            else {
                logger.debug("position is opened because positionAmt is  ${updated.positionAmt}")
                positionRepository.updatePosition(updated)
                strategyPositionMap.replace(updated.strategyKey, updated)
            }
        }
    }

    fun syncPosition(symbol: Symbol) {
        strategyPositionMap.values.find {
            it.symbol == symbol
        }?.let {
            val updated = Position.sync(it)
            positionRepository.updatePosition(updated)
            strategyPositionMap.replace(updated.strategyKey, updated)
            eventUseCase.publishEvent(PositionEvent.PositionSyncedEvent(updated))
        }
    }

    fun closePosition(position: Position) {
        accountUseCase.setUnSynced()
        positionRepository.deletePosition(position)
        strategyPositionMap.remove(position.strategyKey)
        exchangeImpl.closePosition(position)
    }

    fun hasPosition(strategyKey: String): Boolean {
        return strategyPositionMap[strategyKey] != null
    }

    fun getAllUsedSymbols(): Set<Symbol> {
        return strategyPositionMap.values.map { it.symbol }.toSet()
    }

    fun getAllLoadedPosition(): List<Position> {
        return strategyPositionMap.values.toList()
    }
}
