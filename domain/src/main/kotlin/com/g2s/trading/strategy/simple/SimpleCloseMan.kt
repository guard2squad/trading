package com.g2s.trading.strategy.simple

import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.event.PositionEvent
import com.g2s.trading.event.StrategyEvent
import com.g2s.trading.history.CloseCondition
import com.g2s.trading.history.ConditionUseCase
import com.g2s.trading.history.TradingAction
import com.g2s.trading.history.TradingAction.STOP_LOSS
import com.g2s.trading.history.TradingAction.TAKE_PROFIT
import com.g2s.trading.indicator.MarkPriceUseCase
import com.g2s.trading.lock.LockUsage
import com.g2s.trading.lock.LockUseCase
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class SimpleCloseMan(
    private val lockUseCase: LockUseCase,
    private val positionUseCase: PositionUseCase,
    private val markPriceUseCase: MarkPriceUseCase,
    private val conditionUseCase: ConditionUseCase,
    private val accountUseCase: AccountUseCase,
    private val strategySpecRepository: StrategySpecRepository
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private const val TYPE = "simple"
    }

    val scheduler = Executors.newScheduledThreadPool(1)

    // simple TYPE의 strategySpec들을 관리
    private val specs: ConcurrentHashMap<String, StrategySpec> =
        strategySpecRepository.findAllServiceStrategySpecByType(TYPE)
            .associateBy { it.strategyKey }
            .let { ConcurrentHashMap(it) }

    // 열린 포지션을 관리
    // TODO: 이제 그냥 열린 포지션은 의미가 없음. 열렸지만, close 주문이 들어가지 않은 포지션이 의미가 있음.
    // 애플리케이션 재시동시 close 주문이 들어갔는지 여부를 확인하고, 들어가지 않았다면 주문 해야함
    private val symbolPositionMap: ConcurrentHashMap<Position.PositionKey, Position> =
        positionUseCase.getAllPositions()
            .filter { position -> specs.keys.contains(position.strategyKey) }
            .associateBy { it.positionKey }
            .let { ConcurrentHashMap(it) }

    // TODO
    // 통계를 통해서 symbol lifeSpan 결정 lifespan은 가격 이벤트가 몇 번 오는지 단위로 환산
    // 결정된 lifeSpan보다 오래 살아 있으면 decay
    // decay 간격은 어떻게? 이것도 퍼센트로
    // 함수 하나 만들면 될것 같은데, opentransaction time이랑 current time 비교하기
    // 아니면 position - 가격 이벤트 올 때마다 count 누적

    @EventListener
    fun handleStartStrategyEvent(event: StrategyEvent.StartStrategyEvent) {
        val spec = event.source
        if (spec.strategyType != TYPE) return
        specs.putIfAbsent(spec.strategyKey, spec)
    }

    @EventListener
    fun handleUpdateStrategyEvent(event: StrategyEvent.UpdateStrategyEvent) {
        val spec = event.source
        if (spec.strategyType != TYPE) return
        specs.replace(spec.strategyKey, spec)
    }

    @EventListener
    fun handleStopStrategyEvent(event: StrategyEvent.StopStrategyEvent) {
        val spec = event.source
        if (spec.strategyType != TYPE) return
        specs.remove(spec.strategyKey)
    }

    @EventListener
    fun handlePositionSyncedEvent(event: PositionEvent.PositionSyncedEvent) {
        // sync된 포지션에 대해서 close 작업 실행
        val position = event.source
        // position must be synced
        assert(position.synced)
        if (!position.synced) {
            logger.debug("position not synced : ${position.symbol.value}")
            return
        }
        // strategy 락을 획득할 때까지 재시도
        val acquired = lockUseCase.acquire(position.strategyKey, LockUsage.CLOSE)
        if (!acquired) {
            logger.debug("strategy lock is not acquired. strategyKey : ${position.strategyKey}, symbol : ${position.symbol}")
            // 재시도 로직
            scheduler.schedule({ handlePositionSyncedEvent(event) }, 1, TimeUnit.SECONDS)
            return
        }
        // close 조건에 따라 익절/손절 주문 넣기
        val availableBalance = accountUseCase.getAvailableBalance(position.asset)
        val entryPrice = BigDecimal(position.entryPrice)
        val tailLength = BigDecimal(position.referenceData["tailLength"].asDouble())
        val spec = specs[position.strategyKey]!!
        val stopLossFactor = BigDecimal(spec.op["stopLossFactor"].asDouble())
        val takeProfitFactor = BigDecimal(spec.op["takeProfitFactor"].asDouble())
        // 익절 조건 처리
        handleOrderCondition(
            position,
            position.orderSide,
            tailLength,
            entryPrice,
            takeProfitFactor,
            TAKE_PROFIT,
            availableBalance
        )
        // 손절 처리
        handleOrderCondition(
            position,
            position.orderSide,
            tailLength,
            entryPrice,
            stopLossFactor,
            STOP_LOSS,
            availableBalance
        )
        // 포지션 닫는 주문 넣음
        positionUseCase.closePosition(
            position,
            OrderType.LIMIT,
            takeProfitPrice = entryPrice.plus(tailLength.multiply(takeProfitFactor)),
            stopLossPrice = entryPrice.minus(tailLength.multiply(stopLossFactor))
        )
        // 포지션이 성공적으로 닫혔으면 map에서 제거
        // TODO: 바로 제거하면 안 됨
        symbolPositionMap.remove(position.positionKey)
        // 릴리즈
        lockUseCase.release(position.strategyKey, LockUsage.CLOSE)
    }

    // 포지션 닫혔으면, 타입으로 체크해서 제거

    private fun handleOrderCondition(
        position: Position,
        side: OrderSide,
        tailLength: BigDecimal,
        entryPrice: BigDecimal,
        factor: BigDecimal, // stopLossFactor or takeProfitFactor
        action: TradingAction,
        availableBalance: BigDecimal
    ) {
        val priceAdjustment = tailLength.multiply(factor)
        val closePrice = when (side) {
            OrderSide.LONG -> {
                if (action == TAKE_PROFIT) entryPrice.plus(priceAdjustment) else entryPrice.minus(priceAdjustment)
            }

            OrderSide.SHORT -> {
                if (action == TAKE_PROFIT) entryPrice.minus(priceAdjustment) else entryPrice.plus(priceAdjustment)
            }
        }

        conditionUseCase.setCloseCondition(
            position = position,
            condition = CloseCondition.SimpleCondition(
                tradingAction = action,
                tailLength = tailLength.toString(),
                tailLengthWithFactor = priceAdjustment.toString(),
                factor = factor.toDouble(),
                entryPrice = entryPrice.toString(),
                closePrice = closePrice.toString(),
                beforeBalance = availableBalance.toDouble()
            ),
            tradingAction = action
        )
    }
}
