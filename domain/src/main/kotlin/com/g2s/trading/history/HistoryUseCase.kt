package com.g2s.trading.history

import com.fasterxml.jackson.databind.JsonNode
import com.g2s.trading.exchange.Exchange
import com.g2s.trading.position.Position
import com.g2s.trading.strategy.StrategySpecRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class HistoryUseCase(
    private val strategySpecRepository: StrategySpecRepository,
    private val historyRepository: HistoryRepository,
    private val exchangeImpl: Exchange
) {
    private val strategyHistoryToggleMap = ConcurrentHashMap<String, Boolean>()
    private val unsyncedOpenPositionHistoryMap = ConcurrentHashMap<OpenHistory, Triple<Position, Long, OpenCondition>>()
    private val unsyncedClosePositionHistoryMap =
        ConcurrentHashMap<CloseHistory, Triple<Position, Long, CloseCondition>>()

    init {
        loadStrategySpecKey()
        strategyHistoryToggleMap.compute("manual") { _, _ ->
            true
        }
    }

    fun recordOpenHistory(position: Position, orderId: Long, openCondition: OpenCondition) {
        val toggle = strategyHistoryToggleMap[position.strategyKey]
        if (toggle == true) {
            val unsyncedHistory = createUnsyncedOpenHistory(position, orderId)
            val historyInfo = exchangeImpl.getHistoryInfo(position, orderId)
            historyInfo?.let {
                val syncedHistory = processOpenHistoryInfo(it, unsyncedHistory, openCondition)
                saveOpenHistory(syncedHistory)
            } ?: run {
                unsyncedOpenPositionHistoryMap.compute(unsyncedHistory) { _, _ ->
                    Triple(
                        position,
                        orderId,
                        openCondition
                    )
                }
                saveOpenHistory(unsyncedHistory)
            }
        }
    }

    fun recordCloseHistory(position: Position, orderId: Long, closeCondition: CloseCondition) {
        val toggle = strategyHistoryToggleMap[position.strategyKey]
        if (toggle == true) {
            // 익절/손절 조건이 모두 포함된 구조체
            val unsyncedHistory = createUnsyncedCloseHistory(position, orderId)
            val historyInfo = exchangeImpl.getHistoryInfo(position, orderId)
            historyInfo?.let {
                val syncedHistory = processCloseHistoryInfo(it, unsyncedHistory, closeCondition)
                saveCloseHistory(syncedHistory)
            } ?: let {
                unsyncedClosePositionHistoryMap.compute(unsyncedHistory) { _, _ ->
                    Triple(
                        position,
                        orderId,
                        closeCondition
                    )
                }
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
            val triple = entry.value
            val position = triple.first
            val orderId = triple.second
            val openCondition = triple.third
            val historyInfo = exchangeImpl.getHistoryInfo(position, orderId)
            historyInfo?.let {    // synced
                val syncedHistory = processOpenHistoryInfo(it, unsyncedHistory, openCondition)
                historyRepository.updateOpenHistory(syncedHistory)
                openToRemove.add(unsyncedHistory)
            }
        }
        unsyncedClosePositionHistoryMap.forEach { entry ->
            val unsyncedHistory = entry.key
            val triple = entry.value
            val position = triple.first
            val orderId = triple.second
            val closeCondition = triple.third
            val historyInfo = exchangeImpl.getHistoryInfo(position, orderId)
            historyInfo?.let {
                val syncedHistory = processCloseHistoryInfo(it, unsyncedHistory, closeCondition)
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

    private fun createUnsyncedOpenHistory(position: Position, orderId: Long) = OpenHistory(
        historyKey = OpenHistory.generateHistoryKey(position),
        position = position,
        strategyKey = position.strategyKey,
        orderId = orderId,
        orderSide = position.orderSide,
        orderType = position.orderType
    )

    private fun createUnsyncedCloseHistory(position: Position, orderId: Long) = CloseHistory(
        historyKey = CloseHistory.generateHistoryKey(position),
        position = position,
        strategyKey = position.strategyKey,
        orderId = orderId,
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

    private fun processOpenHistoryInfo(
        historyInfo: JsonNode,
        unsyncedHistory: OpenHistory,
        openCondition: OpenCondition
    ): OpenHistory {
        val transactionTime = historyInfo.first().get("time").asLong()
        val commission = historyInfo.sumOf { node -> node.get("commission").asDouble() }
        val afterBalance = exchangeImpl.getCurrentBalance(transactionTime)
        val syncedQuoteQty: Double = historyInfo.sumOf { node -> node.get("quoteQty").asDouble() }
        val syncedHistory = unsyncedHistory.copy(
            openCondition = openCondition,
            transactionTime = transactionTime,
            commission = commission,
            afterBalance = afterBalance,
            syncedQuoteQty = syncedQuoteQty
        )

        return syncedHistory
    }

    private fun processCloseHistoryInfo(
        historyInfo: JsonNode,
        unsyncedHistory: CloseHistory,
        closeCondition: CloseCondition
    ): CloseHistory {
        val transactionTime = historyInfo.first().get("time").asLong()
        val realizedPnl = historyInfo.sumOf { node -> node.get("realizedPnl").asDouble() }
        val commission = historyInfo.sumOf { node -> node.get("commission").asDouble() }
        val afterBalance = exchangeImpl.getCurrentBalance(transactionTime)
        val syncedQuoteQty: Double = historyInfo.sumOf { node -> node.get("quoteQty").asDouble() }
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
