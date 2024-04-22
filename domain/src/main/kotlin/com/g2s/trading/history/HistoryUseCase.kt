package com.g2s.trading.history

import com.g2s.trading.exchange.Exchange
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
    private val unsyncedOpenPositionHistoryMap = ConcurrentHashMap<Position, OpenCondition>()
    private val unsyncedClosePositionHistoryMap = ConcurrentHashMap<Position, CloseCondition>()
    private val logger = LoggerFactory.getLogger(this.javaClass)

    init {
        loadStrategySpecKey()
        strategyHistoryToggleMap.compute("manual") { _, _ ->
            true
        }
    }

    fun recordOpenHistory(position: Position) {
        if (strategyHistoryToggleMap[position.strategyKey]!!) {
            val openCondition = conditionUseCase.getOpenCondition(position)
            val openHistory = exchangeImpl.getOpenHistory(position, openCondition)
            if (openHistory.transactionTime == 0L) {
                unsyncedOpenPositionHistoryMap.compute(position) { _, _ -> openCondition }
            }
            conditionUseCase.removeOpenCondition(position)
            logger.debug(openHistory.toString())
            historyRepository.saveHistory(openHistory)
        }
    }

    fun recordCloseHistory(position: Position) {
        if (strategyHistoryToggleMap[position.strategyKey]!!) {
            val closeCondition = conditionUseCase.getCloseCondition(position)
            val closeHistory = exchangeImpl.getCloseHistory(position, closeCondition)
            if (closeHistory.transactionTime == 0L) {
                unsyncedClosePositionHistoryMap.compute(position) { _, _ -> closeCondition }
            }
            conditionUseCase.removeCloseCondition(position)
            logger.debug(closeHistory.toString())
            historyRepository.saveHistory(closeHistory)
        }
    }

    @Scheduled(fixedDelay = 2000)
    fun syncHistory() {
        val toRemoveOpen = mutableListOf<Position>()
        val toRemoveClose = mutableListOf<Position>()

        unsyncedOpenPositionHistoryMap.forEach { entry ->
            val openHistory = exchangeImpl.getOpenHistory(entry.key, entry.value)
            if (openHistory.transactionTime != 0L) {
                historyRepository.updateOpenHistory(openHistory)
                toRemoveOpen.add(entry.key)
            }
        }
        unsyncedClosePositionHistoryMap.forEach { entry ->
            val closeHistory = exchangeImpl.getCloseHistory(entry.key, entry.value)
            if (closeHistory.transactionTime != 0L) {
                historyRepository.updateCloseHistory(closeHistory)
                toRemoveClose.add(entry.key)
            }
        }

        toRemoveOpen.forEach { unsyncedOpenPositionHistoryMap.remove(it) }
        toRemoveClose.forEach { unsyncedClosePositionHistoryMap.remove(it) }
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
