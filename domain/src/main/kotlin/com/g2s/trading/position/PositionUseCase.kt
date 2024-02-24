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
    private val strategyPositionMap = ConcurrentHashMap<String, Position>() // StrategyKey : Position

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
        logger.debug("open position\n - symbol:${position.symbol}")
        strategyPositionMap.computeIfAbsent(position.strategyKey) { _ ->
            logger.debug("map size = ${strategyPositionMap.size}\n")
            // set account unsynced
            accountUseCase.setUnSynced()
            // update unsynced position to DB
            positionRepository.savePosition(position)
            // send order
            exchangeImpl.openPosition(position)
            // save unsynced position to map
            position
        }
        logger.debug("map size = ${strategyPositionMap.size}\n")
    }

    fun refreshPosition(positionRefreshData: PositionRefreshData) {
        logger.debug("refreshPosition")
        strategyPositionMap.values.find {
            it.symbol == positionRefreshData.symbol
        }?.let { old ->
            val updated = Position.update(old, positionRefreshData)
            // opened   position 때만 sync 처리하면 됨.
            // 이 조건문에 문제가 있다.
            if (updated.positionAmt != 0.0) {
                logger.debug("position is opened because positionAmt is  ${updated.positionAmt}")
                positionRepository.updatePosition(updated)
                strategyPositionMap.replace(updated.strategyKey, updated)
            }
        }
    }

    fun syncPosition(symbol: Symbol) {
        logger.debug("syncPosition")
        strategyPositionMap.values.find {
            it.symbol == symbol
        }?.let {old ->
            val synced = Position.sync(old)
            positionRepository.updatePosition(synced)
            logger.debug("position synced in DB\n")
            strategyPositionMap.replace(synced.strategyKey, synced)
            logger.debug("position synced in map\n")
            eventUseCase.publishEvent(PositionEvent.PositionSyncedEvent(synced))
        }
    }

    fun closePosition(position: Position) {
        accountUseCase.setUnSynced()
        positionRepository.deletePosition(position)
        strategyPositionMap.remove(position.strategyKey)
        exchangeImpl.closePosition(position)
        logger.debug("$position closed\n")
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
