package com.g2s.trading.position

import com.g2s.trading.EventUseCase
import com.g2s.trading.PositionEvent
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.symbol.Symbol
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
    private val positionMap = ConcurrentHashMap<Symbol, Position>()

    init {
        loadPositions()
    }

    private fun loadPositions() {
        // db 조회
        val positions = positionRepository.findAllPositions()
        logger.debug("load positions: ${positions.size}")
        // map 저장
        positions.forEach { position ->
            positionMap[position.symbol] = position
        }
    }

    fun openPosition(position: Position) {
        logger.debug("open position\n - symbol:${position.symbol}")
        val currentValue = positionMap.computeIfAbsent(position.symbol) { _ ->
            logger.debug("map size = ${positionMap.size}\n")
            // set account unsynced
            accountUseCase.setUnSynced()
            // update unsynced position to DB
            positionRepository.savePosition(position)
            // save unsynced position to map
            position
        }

        if (currentValue == position) {
            // send order
            exchangeImpl.openPosition(position)
        }

        logger.debug("map size = ${positionMap.size}\n")
    }

    fun refreshPosition(positionRefreshData: PositionRefreshData) {
        logger.debug("refreshPosition")
        positionMap.values.find {
            it.symbol == positionRefreshData.symbol
        }?.let { old ->
            val updated = Position.update(old, positionRefreshData)
            if (updated.positionAmt != 0.0) {
                logger.debug("position is opened because positionAmt is  ${updated.positionAmt}")
                positionRepository.updatePosition(updated)
                positionMap.replace(updated.symbol, updated)
            }
        }
    }

    fun syncPosition(symbol: Symbol) {
        logger.debug("syncPosition")
        positionMap.values.find {
            it.symbol == symbol
        }?.let { old ->
            val synced = Position.sync(old)
            positionRepository.updatePosition(synced)
            logger.debug("position synced in DB\n")
            positionMap.replace(synced.symbol, synced)
            logger.debug("position synced in map\n")
            eventUseCase.publishEvent(PositionEvent.PositionSyncedEvent(synced))
        }
    }

    fun closePosition(position: Position) {
        accountUseCase.setUnSynced()
        exchangeImpl.closePosition(position)
        positionRepository.deletePosition(position)
        positionMap.remove(position.symbol)
        logger.debug("$position closed\n")
    }

    fun getAllUsedSymbols(): Set<Symbol> {
        return positionMap.keys
    }

    fun getAllLoadedPosition(): List<Position> {
        return positionMap.values.toList()
    }
}
