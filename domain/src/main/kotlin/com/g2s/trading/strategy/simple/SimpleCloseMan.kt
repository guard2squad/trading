package com.g2s.trading.strategy.simple

import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.event.PositionEvent
import com.g2s.trading.event.StrategyEvent
import com.g2s.trading.history.CloseCondition
import com.g2s.trading.history.TradingAction
import com.g2s.trading.history.TradingAction.STOP_LOSS
import com.g2s.trading.history.TradingAction.TAKE_PROFIT
import com.g2s.trading.lock.LockUsage
import com.g2s.trading.lock.LockUseCase
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderStrategy
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionUseCase
import com.g2s.trading.strategy.StrategySpec
import com.g2s.trading.strategy.StrategySpecRepository
import com.g2s.trading.symbol.Symbol
import com.g2s.trading.symbol.SymbolUseCase
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class SimpleCloseMan(
    private val lockUseCase: LockUseCase,
    private val positionUseCase: PositionUseCase,
    private val accountUseCase: AccountUseCase,
    private val symbolUseCase: SymbolUseCase,
    private val strategySpecRepository: StrategySpecRepository
) {
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val logger = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private const val TYPE = "simple"
    }

    // simple TYPE의 strategySpec들을 관리
    private val specs: ConcurrentHashMap<String, StrategySpec> =
        strategySpecRepository.findAllServiceStrategySpecByType(TYPE)
            .associateBy { it.strategyKey }
            .let { ConcurrentHashMap(it) }

    // 열린 포지션을 관리
    // TODO: 이제 그냥 열린 포지션은 의미가 없음. 열렸지만, close 주문이 들어가지 않은 포지션이 의미가 있음.
    // 애플리케이션 재시동시 close 주문이 들어갔는지 여부를 확인하고, 들어가지 않았다면 주문 해야함
    private val opendPositions: ConcurrentHashMap<Position.PositionKey, Position> =
        positionUseCase.getAllPositions()
            .filter { position -> specs.keys.contains(position.strategyKey) }
            .associateBy { it.positionKey }
            .let { ConcurrentHashMap(it) }

    // CLOSE 주문을 넣은 포지션들을 관리
    private val pendingPositions = ConcurrentHashMap<Position, Unit>()

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
        // 익절 조건 생성
        val takeProfitCondition = handleOrderCondition(
            position.orderSide,
            tailLength,
            entryPrice,
            takeProfitFactor,
            TAKE_PROFIT,
            availableBalance
        )
        // 손절 조건 생성
        val stopLossCondition = handleOrderCondition(
            position.orderSide,
            tailLength,
            entryPrice,
            stopLossFactor,
            STOP_LOSS,
            availableBalance
        )
        // 포지션 닫는 주문 넣음
        val hasClosed = positionUseCase.closePosition(
            position,
            OrderStrategy.DUAL_LIMIT,
            takeProfitPrice = caculateLimitPrice(
                entryPrice.plus(tailLength.multiply(takeProfitFactor)),
                symbol = position.symbol
            ),
            stopLossPrice = caculateLimitPrice(
                entryPrice.minus(tailLength.multiply(stopLossFactor)),
                symbol = position.symbol
            ),
            takeProfitCondition = takeProfitCondition,
            stopLossCondition = stopLossCondition
        )
        if (hasClosed) {
            // 정상적으로 주문 넣은 경우
            pendingPositions.compute(position) { _, _ -> }
            opendPositions.remove(position.positionKey)
            // 릴리즈
            lockUseCase.release(position.strategyKey, LockUsage.CLOSE)
        }
        else {
            // 주문 실패 했으면 릴리즈하고 재시도
            lockUseCase.release(position.strategyKey, LockUsage.CLOSE)
            scheduler.schedule({ handlePositionSyncedEvent(event) }, 1, TimeUnit.SECONDS)
        }
    }

    @EventListener
    fun handlePositionFilledEvent(event: PositionEvent.PositionFilledEvent) {
        val position = event.source
        pendingPositions.remove(position)
    }

    /**
     * 주문 조건에 필요한 데이터를 가공하고 CloseCondition을 반환합니다.
     * @return CloseCondition
     */
    private fun handleOrderCondition(
        side: OrderSide,
        tailLength: BigDecimal,
        entryPrice: BigDecimal,
        factor: BigDecimal, // stopLossFactor or takeProfitFactor
        action: TradingAction,
        availableBalance: BigDecimal
    ): CloseCondition {
        val priceAdjustment = tailLength.multiply(factor)
        val closePrice = when (side) {
            OrderSide.LONG -> {
                if (action == TAKE_PROFIT) entryPrice.plus(priceAdjustment) else entryPrice.minus(priceAdjustment)
            }

            OrderSide.SHORT -> {
                if (action == TAKE_PROFIT) entryPrice.minus(priceAdjustment) else entryPrice.plus(priceAdjustment)
            }
        }

        val condition = CloseCondition.SimpleCondition(
            tradingAction = action,
            tailLength = tailLength.toString(),
            tailLengthWithFactor = priceAdjustment.toString(),
            factor = factor.toDouble(),
            entryPrice = entryPrice.toDouble(),
            closePrice = closePrice.toDouble(),
            beforeBalance = availableBalance.toDouble()
        )

        return condition
    }

    /**
     * Limit 주문 가격을 계산하는 함수. 입력된 가격을 암호화폐의 정확도(precision)에 맞게 조정하고,
     * 지정된 tickSize에 따라 가격을 조정합니다. 아래의 조건들을 만족하지 않을 경우 주문이 실패합니다:
     *
     * 1. (price - minPrice) % tickSize == 0
     *    - 이 조건을 만족하지 못할 경우, 가격이 tickSize의 배수로 증가하지 않음을 의미합니다.
     *    - 실패 응답: -4014 PRICE_NOT_INCREASED_BY_TICK_SIZE, msg: Price not increased by tick size.
     * 2. 가격의 정확도(precision)를 만족해야 함
     *    - 예) BTC: pricePrecision == 2, price=63976.15276916인 경우, 정확도가 8로 설정된 최대값을 초과합니다.
     *    - 실패 응답: -1111 BAD_PRECISION, msg: Precision is over the maximum defined for this asset.
     *
     * @param price 원래 가격(raw state)을 Double 형태로 입력받습니다.
     * @param symbol 자산의 식별자입니다. 조건을 구하기 위해 사용됩니다.
     * @return 조정된 가격을 Double 형태로 반환합니다.
     */
    private fun caculateLimitPrice(price: BigDecimal, symbol: Symbol): Double {
        // 정확도에 따라 버림처리
        val precision = symbolUseCase.getPricePrecision(symbol)
        val truncatedPrice = price.setScale(precision, RoundingMode.CEILING)


        // tickSize조건에 따라 가격 조정
        val tickSize = BigDecimal(symbolUseCase.getTickSize(symbol)).setScale(precision, RoundingMode.FLOOR)
        val minPrice = BigDecimal(symbolUseCase.getMinPrice(symbol)).setScale(precision, RoundingMode.FLOOR)
        val remainder = ((truncatedPrice - minPrice) % tickSize).setScale(precision, RoundingMode.FLOOR)
        val quotient = (truncatedPrice - remainder).setScale(precision, RoundingMode.FLOOR)
        if (remainder > tickSize / BigDecimal(2)) {
            return (quotient + tickSize).setScale(precision, RoundingMode.FLOOR).toDouble()
        }
        // TODO: 나머지를 롱인지, 숏인지에 따라서 올림, 버림해도 좋을 것 같음
        return quotient.setScale(precision, RoundingMode.FLOOR).toDouble()
    }
}
