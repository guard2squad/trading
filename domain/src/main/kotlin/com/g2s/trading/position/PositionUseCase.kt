package com.g2s.trading.position

import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.event.EventUseCase
import com.g2s.trading.event.PositionEvent
import com.g2s.trading.exceptions.OrderFailException
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.history.HistoryUseCase
import com.g2s.trading.order.OrderType
import com.g2s.trading.symbol.Symbol
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

@Service
class PositionUseCase(
    private val exchangeImpl: Exchange,
    private val accountUseCase: AccountUseCase,
    private val eventUseCase: EventUseCase,
    private val historyUseCase: HistoryUseCase,
    private val positionRepository: PositionRepository
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val positionMap = ConcurrentHashMap<Position.PositionKey, Position>()
    private val pendingPositions = ConcurrentHashMap<Long, Position>()

    init {
        loadPositions()
    }

    private fun loadPositions() {
        // db 조회
        val positions = positionRepository.findAllPositions()
        logger.debug("load positions: ${positions.size}")
        // map 저장
        positions.forEach { position ->
            positionMap[position.positionKey] = position
        }
    }

    fun openPosition(position: Position) {
        logger.debug("포지션 오픈 - symbol:${position.symbol}")
        try {

            val currentValue = positionMap.computeIfAbsent(position.positionKey) { _ ->
                logger.debug("openPostion 실행 전 map size = ${positionMap.size}\n")
                accountUseCase.setUnSynced()
                positionRepository.savePosition(position)
                position
            }

            if (currentValue == position) {
                exchangeImpl.openPosition(position)
                historyUseCase.recordOpenHistory(position)
            }
        } catch (e: OrderFailException) {
            logger.warn(e.message)
            positionMap.remove(position.positionKey)
            positionRepository.deletePosition(position)
            accountUseCase.syncAccount()
        }
        logger.debug("openPostion 실행 후 map size = ${positionMap.size}\n")
    }

    fun refreshPosition(positionRefreshData: PositionRefreshData) {
        positionMap.values.find {
            it.symbol == positionRefreshData.symbol
        }?.let { old ->
            val updated = Position.update(old, positionRefreshData)
            if (updated.positionAmt != 0.0) {
                positionRepository.updatePosition(updated)
                positionMap.replace(updated.positionKey, updated)
            }
        }
    }

    fun syncPosition(symbol: Symbol) {
        positionMap.values.find {
            it.symbol == symbol
        }?.let { old ->
            val synced = Position.sync(old)
            positionRepository.updatePosition(synced)
            positionMap.replace(synced.positionKey, synced)
            eventUseCase.publishEvent(PositionEvent.PositionSyncedEvent(synced))
        }
    }

    /**
     *  포지션을 닫고, 주문이 실패할 경우 롤백합니다. 주문 성공 여부를 반환힙니다.
     *
     * @param position The trading position to close.
     * @param orderType Order Type
     * @param takeProfitPrice Order Type이 Limit일 때 익절 가격
     * @param stopLossPrice Order Type이 Limit일 때 손절 가격
     * @return [Boolean] indicating success (`true`) or failure (`false`) of the operation.
     */
    fun closePosition(
        position: Position,
        orderType: OrderType,
        takeProfitPrice: BigDecimal? = null,
        stopLossPrice: BigDecimal? = null
    ): Boolean {
        when (orderType) {
            OrderType.MARKET -> {
                val originalPosition = position.copy()

                try {
                    accountUseCase.setUnSynced()
                    positionRepository.deletePosition(position)
                    positionMap.remove(position.positionKey)
                    val orderId = exchangeImpl.closePosition(position, OrderType.MARKET)
                    historyUseCase.recordCloseHistory(position, orderId)
                    return true
                } catch (e: OrderFailException) {
                    logger.warn(e.message)
                    positionMap[originalPosition.positionKey] = originalPosition
                    positionRepository.savePosition(originalPosition)
                    accountUseCase.syncAccount()
                    return false
                }
            }

            OrderType.LIMIT -> {
                assert(stopLossPrice != null)
                assert(takeProfitPrice != null)
                // 주문이 채결되면 계좌 synced
                accountUseCase.setUnSynced()
                // 익절 주문
                try {
                    val stopLossOrderId = exchangeImpl.closePosition(position, OrderType.LIMIT, stopLossPrice!!)
                    pendingPositions.compute(stopLossOrderId) { _, _ -> position }
                } catch (e: OrderFailException) {
                    accountUseCase.syncAccount()
                    logger.warn(e.message)
                }
                // 손절 주문
                try {
                    val takeProfitOrderId = exchangeImpl.closePosition(position, OrderType.LIMIT, takeProfitPrice!!)
                    pendingPositions.compute(takeProfitOrderId) { _, _ -> position }
                } catch (e: OrderFailException) {
                    accountUseCase.syncAccount()
                    logger.warn(e.message)
                }
                return true
            }
        }
    }

    /**
     * 주문이 체결되면 주문에 해당하는 포지션을 처리합니다.
     * - DB에서 포지션 삭제
     * - 도메인의 포지션 맵에서 포지션 삭제
     * - close history 기록
     *
     * @param orderId 체결된 주문의 ID
     */
    fun processFilledClosedPosition(orderId: Long) {
        pendingPositions.remove(orderId)?.also { position ->
            positionRepository.deletePosition(position)
            positionMap.remove(position.positionKey)
            historyUseCase.recordCloseHistory(position, orderId)
            pendingPositions.entries.find { it.value == position }?.let {
                pendingPositions.remove(it.key)
            }
        }
    }

    fun getAllUsedSymbols(): Set<Symbol> {
        return positionMap.values.map { position -> position.symbol }.toSet()
    }

    fun getAllPositions(): List<Position> {
        return positionMap.values.toList()
    }
}
