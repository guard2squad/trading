package com.g2s.trading.position

import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.event.EventUseCase
import com.g2s.trading.event.PositionEvent
import com.g2s.trading.exceptions.OrderFailException
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.history.CloseCondition
import com.g2s.trading.history.HistoryUseCase
import com.g2s.trading.history.OpenCondition
import com.g2s.trading.order.OrderStrategy
import com.g2s.trading.order.OrderType
import com.g2s.trading.symbol.Symbol
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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
    private val openedPositions = ConcurrentHashMap<Position.PositionKey, Position>()

    // Pending은 LIMIT 주문인 포지션을 닫을 때 발생합니다.
    private val pendingPositions = ConcurrentHashMap<Long, Pair<Position, CloseCondition>>()

    init {
        loadPositions()
    }

    private fun loadPositions() {
        // db 조회
        val positions = positionRepository.findAllPositions()
        logger.debug("load positions: ${positions.size}")
        // map 저장
        positions.forEach { position ->
            openedPositions[position.positionKey] = position
        }
    }

    fun openPosition(position: Position, openCondition: OpenCondition) {
        logger.debug("포지션 오픈 - symbol:${position.positionKey}")
        try {
            val currentValue = openedPositions.computeIfAbsent(position.positionKey) { _ ->
                logger.debug("openPostion 실행 전 map size = ${openedPositions.size}\n")
                // MARKET으로 주문하고 FILLED되면 계좌 sync
                accountUseCase.setUnSynced()
                positionRepository.savePosition(position)
                position
            }

            if (currentValue == position) {
                val orderId = exchangeImpl.openPosition(position)
                historyUseCase.recordOpenHistory(position, orderId, openCondition)
            }
        } catch (e: OrderFailException) {
            logger.warn(e.message)
            openedPositions.remove(position.positionKey)
            positionRepository.deletePosition(position)
            accountUseCase.syncAccount()
        }
        logger.debug("openPostion 실행 후 map size = ${openedPositions.size}\n")
    }

    fun refreshPosition(positionRefreshData: PositionRefreshData) {
        openedPositions.values.find {
            it.symbol == positionRefreshData.symbol
        }?.let { old ->
            val updated = Position.update(old, positionRefreshData)
            if (updated.positionAmt != 0.0) {
                positionRepository.updatePosition(updated)
                openedPositions.replace(updated.positionKey, updated)
            }
        }
    }

    fun syncPosition(symbol: Symbol) {
        openedPositions.values.find {
            it.symbol == symbol
        }?.let { old ->
            val synced = Position.sync(old)
            positionRepository.updatePosition(synced)
            openedPositions.replace(synced.positionKey, synced)
            eventUseCase.publishEvent(PositionEvent.PositionSyncedEvent(synced))
        }
    }

    /**
     *  포지션을 닫고, 주문이 실패할 경우 롤백합니다. 주문 성공 여부를 반환힙니다.
     *
     * @param position The trading position to close.
     * @param orderStrategy 주문 전략
     * @param takeProfitPrice Order Type이 Limit일 때 익절 가격
     * @param stopLossPrice Order Type이 Limit일 때 손절 가격
     * @param takeProfitCondition 익절 조건
     * @param stopLossCondition 손절 조건
     * @param marketCloseCondition 시장가 판매 조건
     * @return [Boolean] indicating success (`true`) or failure (`false`) of the operation.
     */
    fun closePosition(
        position: Position,
        orderStrategy: OrderStrategy,
        takeProfitPrice: Double = 0.0,
        stopLossPrice: Double = 0.0,
        takeProfitCondition: CloseCondition? = null,
        stopLossCondition: CloseCondition? = null,
        marketCloseCondition: CloseCondition? = null
    ): Boolean {
        when (orderStrategy) {
            OrderStrategy.MARKET -> {
                val originalPosition = position.copy()

                try {
                    accountUseCase.setUnSynced()
                    positionRepository.deletePosition(position)
                    openedPositions.remove(position.positionKey)
                    val orderId = exchangeImpl.closePosition(position, OrderType.MARKET)
                    pendingPositions.compute(orderId) { _, _ -> Pair(position, marketCloseCondition!!) }
                    return true
                } catch (e: OrderFailException) {
                    logger.warn(e.message)
                    openedPositions[originalPosition.positionKey] = originalPosition
                    positionRepository.savePosition(originalPosition)
                    accountUseCase.syncAccount()
                    return false
                }
            }

            OrderStrategy.DUAL_LIMIT -> {
                assert(stopLossPrice != 0.0)
                assert(takeProfitPrice != 0.0)
                logger.debug("진입가: ${position.entryPrice}")
                // 익절 주문
                val takeProfitOrderId: Long
                try {
                    logger.debug("TAKE_PROFIT_LIMIT 익절주문: ${position.positionKey}")
                    logger.debug("익절가: $takeProfitPrice")
                    takeProfitOrderId = exchangeImpl.closePosition(position, OrderType.TAKE_PROFIT, takeProfitPrice)
                    pendingPositions.compute(takeProfitOrderId) { _, _ -> Pair(position, takeProfitCondition!!) }
                } catch (e: OrderFailException) {
                    logger.warn(e.message)
                    logger.warn("익절 주문 실패로 시장가로 다시 주문")
                    return false
                }
                // 손절 주문
                try {
                    logger.debug("STOP_LIMIT 손절주문: ${position.positionKey}")
                    logger.debug("손절가: $stopLossPrice")
                    val stopLossOrderId = exchangeImpl.closePosition(position, OrderType.STOP, stopLossPrice)
                    pendingPositions.compute(stopLossOrderId) { _, _ -> Pair(position, stopLossCondition!!) }
                } catch (e: OrderFailException) {
                    logger.warn(e.message)
                    logger.warn("손절 주문 실패로 시장가로 다시 주문")
                    return false
                }
                return true
            }
        }
    }

    /**
     * 주문이 체결되면 주문에 해당하는 포지션을 처리합니다.
     * - pendingPositions 맵에서 포지션 삭제
     * - DB에서 포지션 삭제
     * - 도메인의 포지션 맵에서 포지션 삭제
     * - close history 기록
     * @param orderId 체결된 주문의 ID
     */
    fun processFilledClosedPosition(orderId: Long) {
        // open 주문이 filled된 경우 return
        val pair = pendingPositions[orderId] ?: return
        val positionToRemove = pair.first
        val orderIdToCancel =
            pendingPositions.entries.find { it.key != orderId && it.value.first == positionToRemove }!!.key
        try {
            // 채결된 포지션이 익절이라면 손절 주문 취소, 손절이라면 익절 주문 취소
            exchangeImpl.cancelOrder(positionToRemove.symbol, orderIdToCancel)
        } catch (e: OrderFailException) {
            logger.warn("주문 취소 실패: " + e.message)
        } finally {
            pendingPositions.remove(orderIdToCancel)
        }

        pendingPositions.remove(orderId)?.also { p ->
            val position = p.first
            val closeCondition = p.second
            positionRepository.deletePosition(position)
            openedPositions.remove(position.positionKey)
            historyUseCase.recordCloseHistory(position, orderId, closeCondition)
            // 각 CLOSE 전략에 FILLED 포지션 발행
            eventUseCase.publishEvent(PositionEvent.PositionFilledEvent(position))
            logger.debug("CLOSE 포지션 체결: ${position.positionKey}")
        }
    }

    fun processCancelledPosition(orderId: Long) {
        pendingPositions.remove(orderId)
    }

    fun getAllUsedSymbols(): Set<Symbol> {
        return openedPositions.values.map { position -> position.symbol }.toSet()
    }

    fun getAllPositions(): List<Position> {
        return openedPositions.values.toList()
    }
}
