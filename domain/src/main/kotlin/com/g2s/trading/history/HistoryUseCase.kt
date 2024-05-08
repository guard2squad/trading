package com.g2s.trading.history

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.history.ConditionUseCase.*
import com.g2s.trading.position.Position
import com.g2s.trading.strategy.StrategySpecRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class HistoryUseCase(
    private val conditionUseCase: ConditionUseCase,
    private val strategySpecRepository: StrategySpecRepository,
    private val historyRepository: HistoryRepository,
    private val exchangeImpl: Exchange
) {
    private val strategyHistoryToggleMap = ConcurrentHashMap<String, Boolean>()
    private val unsyncedOpenPositionHistoryMap = ConcurrentHashMap<OpenHistory, Position>()
    private val unsyncedClosePositionHistoryMap = ConcurrentHashMap<CloseHistory, Pair<Position, Long>>()
    private val logger = LoggerFactory.getLogger(this.javaClass)

    init {
        loadStrategySpecKey()
        strategyHistoryToggleMap.compute("manual") { _, _ ->
            true
        }
    }

    fun recordOpenHistory(position: Position) {
        val toggle = strategyHistoryToggleMap[position.strategyKey]
        if (toggle == true) {
            val openCondition = conditionUseCase.getOpenCondition(position)
            val unsyncedHistory = createUnsyncedOpenHistory(position, openCondition)
            val historyInfo = exchangeImpl.getOpenHistoryInfo(position)
            historyInfo?.let {
                val transactionTime = it.first().get("time").asLong()
                val commission = it.sumOf { node -> node.get("commission").asDouble() }
                val afterBalance = exchangeImpl.getCurrentBalance(transactionTime)
                val syncedHistory = unsyncedHistory.copy(
                    transactionTime = transactionTime,
                    commission = commission,
                    afterBalance = afterBalance
                )
                saveOpenHistory(syncedHistory)
            } ?: run {
                unsyncedOpenPositionHistoryMap.compute(unsyncedHistory) { _, _ -> position }
                saveOpenHistory(unsyncedHistory)
            }

            conditionUseCase.removeOpenCondition(position)
        }
    }

    fun recordCloseHistory(position: Position, orderId: Long) {
        val toggle = strategyHistoryToggleMap[position.strategyKey]
        if (toggle == true) {
            // 익절/손절 조건이 모두 포함된 구조체
            val closeConditionStruct = conditionUseCase.getCloseCondition(position)
            val unsyncedHistory = createUnsyncedCloseHistory(position)
            val historyInfo = exchangeImpl.getCloseHistoryInfo(position, orderId)
            historyInfo?.let {
                val syncedHistory = processCloseHistoryInfo(it, unsyncedHistory, closeConditionStruct)
                saveCloseHistory(syncedHistory)
                conditionUseCase.removeCloseCondition(position)
            } ?: let {
                unsyncedClosePositionHistoryMap.compute(unsyncedHistory) { _, _ -> Pair(position, orderId) }
                saveCloseHistory(unsyncedHistory)
            }
        }
    }


    @Scheduled(fixedDelay = 2000)
    fun syncHistory() {
        val openToRemove = mutableListOf<OpenHistory>()
        val closeToRemove = mutableListOf<CloseHistory>()
        unsyncedOpenPositionHistoryMap.forEach { entry ->
            val unsyncedHistory = entry.key
            val position = entry.value
            val historyInfo = exchangeImpl.getOpenHistoryInfo(position)
            historyInfo?.let {    // synced
                val transactionTime = it.first().get("time").asLong()
                val commission = it.sumOf { node -> node.get("commission").asDouble() }
                val afterBalance = exchangeImpl.getCurrentBalance(transactionTime)
                val syncedQuoteQty: Double = it.sumOf { node -> node.get("quoteQty").asDouble() }
                val syncedHistory = unsyncedHistory.copy(
                    transactionTime = transactionTime,
                    commission = commission,
                    afterBalance = afterBalance,
                    syncedQuoteQty = syncedQuoteQty
                )
                historyRepository.updateOpenHistory(syncedHistory)
                openToRemove.add(unsyncedHistory)
            }
        }
        unsyncedClosePositionHistoryMap.forEach { entry ->
            val unsyncedHistory = entry.key
            val pair = entry.value
            val position = pair.first
            val orderId = pair.second
            val closeConditionStruct = conditionUseCase.getCloseCondition(position)
            val historyInfo = exchangeImpl.getCloseHistoryInfo(position, orderId)
            historyInfo?.let {
                val syncedHistory = processCloseHistoryInfo(it, unsyncedHistory, closeConditionStruct)
                historyRepository.updateCloseHistory(syncedHistory)
                closeToRemove.add(unsyncedHistory)
            }
        }

        openToRemove.forEach { unsyncedOpenPositionHistoryMap.remove(it) }
        closeToRemove.forEach { unsyncedClosePositionHistoryMap.remove(it) }
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

    private fun createUnsyncedOpenHistory(position: Position, openCondition: OpenCondition) = OpenHistory(
        historyKey = OpenHistory.generateHistoryKey(position),
        position = position,
        strategyKey = position.strategyKey,
        openCondition = openCondition,
        orderSide = position.orderSide,
        orderType = position.orderType
    )

    private fun createUnsyncedCloseHistory(position: Position) = CloseHistory(
        historyKey = CloseHistory.generateHistoryKey(position),
        position = position,
        strategyKey = position.strategyKey,
        orderSide = position.orderSide,
        orderType = position.orderType
    )

    private fun saveOpenHistory(history: OpenHistory) {
        historyRepository.saveOpenHistory(history)
    }

    private fun saveCloseHistory(history: CloseHistory) {
        historyRepository.saveCloseHistory(history)
    }

    private fun loadStrategySpecKey() {
        strategySpecRepository.findAllServiceStrategySpec().forEach {
            strategyHistoryToggleMap.computeIfAbsent(it.strategyKey) {
                true
            }
        }
    }

    private fun processCloseHistoryInfo(
        historyInfo: JsonNode,
        unsyncedHistory: CloseHistory,
        closeConditionStruct: CloseConditionStruct
    ): CloseHistory {
        val transactionTime = historyInfo.first().get("time").asLong()
        val realizedPnl = historyInfo.sumOf { node -> node.get("realizedPnl").asDouble() }
        val commission = historyInfo.sumOf { node -> node.get("commission").asDouble() }
        val afterBalance = exchangeImpl.getCurrentBalance(transactionTime)
        val syncedQuoteQty: Double = historyInfo.sumOf { node -> node.get("quoteQty").asDouble() }
        val closeCondition =
            if (realizedPnl > 0) closeConditionStruct.takeProfit else closeConditionStruct.stopLoss
        val syncedHistory = unsyncedHistory.copy(
            closeCondition = closeCondition,
            transactionTime = transactionTime,
            realizedPnL = realizedPnl,
            commission = commission,
            afterBalance = afterBalance,
            syncedQuoteQty = syncedQuoteQty
        )

        return syncedHistory
    }
}
