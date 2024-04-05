package com.g2s.trading.history

import com.g2s.trading.TradingEvent
import com.g2s.trading.account.AccountUseCase
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.position.Position
import com.g2s.trading.strategy.StrategySpecRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class HistoryUseCase(
    val conditionUseCase: ConditionUseCase,
    val accountUseCase: AccountUseCase,
    val strategySpecRepository: StrategySpecRepository,
    val exchangeImpl: Exchange
) {
    private val strategyHistoryToggleMap = ConcurrentHashMap<String, Boolean>()
    private val syncedPositions = ConcurrentHashMap<String, Position>()
    private val closedPositions = ConcurrentHashMap<String, Position>()

    init {
        loadStrategySpecKey()
        strategyHistoryToggleMap.compute("manual") { _, _ ->
            true
        }
    }

    fun setSyncedPosition(position: Position) {
        val clientId = exchangeImpl.getClientIdAtOpen(position)
        syncedPositions.compute(clientId) { _, _ ->
            position
        }
    }

    fun setClosedPosition(position: Position) {
        val clientId = exchangeImpl.getClientIdAtClose(position)
        closedPositions.compute(clientId) { _, _ ->
            position
        }
    }

    @EventListener
    fun recordOpenHistory(event: TradingEvent.CommissionEvent) {
        val position = syncedPositions[event.source.clientId]!!
        if (strategyHistoryToggleMap[position.strategyKey]!!) {
            val openHistory = History.Open(
                historyKey = History.HistoryKey.from(position),
                position = position,
                strategyKey = position.strategyKey,
                openCondition = conditionUseCase.getOpenCondition(position),
                orderSide = position.orderSide,
                orderType = position.orderType,
                transactionTime = position.openTransactionTime,
                commission = event.source.commission,
                afterBalance = accountUseCase.getBalance(position.asset, position.openTransactionTime),
            )
            // TODO(DB에 저장)
            println(openHistory)
        }
    }

    @EventListener
    fun recordCloseHistory(event: TradingEvent.RealizedProfitAndCommissionEvent) {
        val position = closedPositions[event.source.first.clientId]!!
        val closeTransactionTime = exchangeImpl.getPositionClosingTime(position)

        val closeHistory = History.Close(
            historyKey = History.HistoryKey.from(position),
            position = position,
            strategyKey = position.strategyKey,
            closeCondition = conditionUseCase.getCloseCondition(position),
            orderSide = position.orderSide,
            orderType = position.orderType,
            transactionTime = closeTransactionTime,
            realizedPnL = event.source.second.realizedProfit,
            commission = event.source.first.commission,
            afterBalance = accountUseCase.getBalance(position.asset, closeTransactionTime),
        )
        conditionUseCase.removeCondition(position)
        // TODO(DB에 저장)
        println(closeHistory)
    }

    fun turnOnHistoryFeature(strategyKey: String) {
        strategyHistoryToggleMap.compute(strategyKey) { _, _ ->
            true
        }
    }

    fun turnOnAllHistoryFeature() {
        for (entry in strategyHistoryToggleMap) {
            entry.setValue(true)
        }
    }

    fun turnOffHistoryFeature(strategyKey: String) {
        strategyHistoryToggleMap.compute(strategyKey) { _, _ ->
            false
        }
    }

    fun turnOffAllHistoryFeature() {
        for (entry in strategyHistoryToggleMap) {
            entry.setValue(false)
        }
    }

    private fun loadStrategySpecKey() {
        strategySpecRepository.findAllServiceStrategySpec().forEach {
            strategyHistoryToggleMap.computeIfAbsent(it.strategyKey) {
                true
            }
        }
    }
}
