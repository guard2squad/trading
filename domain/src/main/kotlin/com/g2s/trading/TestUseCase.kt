package com.g2s.trading

import com.g2s.trading.history.HistoryRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import kotlin.streams.asSequence

@Service
class TestUseCase(
    private val historyRepository: HistoryRepository
) {

    fun getAllHistories() {

        val rawHistories = historyRepository.getAllHistory("simple01")

        val grouped = rawHistories.groupBy { it["historyKey"].textValue() }
            .filterNot { it.value.size < 2 }
            .entries.groupBy { (key, values) ->
                val openHistory = values.first { value ->
                    value["openCondition"] != null
                }

                openHistory["openCondition"]["patten"].textValue()
            }
        var profitMoney = BigDecimal(0)
        var lossMoney = BigDecimal(0)
        val results = grouped.map { (_, historiesByOpenType) ->
            val closeHistories =
                historiesByOpenType.stream().asSequence().flatMap { it.value }.filter { it["closeCondition"] != null }
                    .toList()

            val takeProfitCloseHistory = closeHistories.filter { n -> n.get("closeCondition").get("tradingAction").asText() == "TAKE_PROFIT" }
            val stopLossCloseHistory = closeHistories.filter { n -> n.get("closeCondition").get("tradingAction").asText() == "STOP_LOSS" }
            val takeProfitCount = takeProfitCloseHistory.size   // pattern별 익절 수
            val stopLossCount = stopLossCloseHistory.size   // pattern별 손절 수
            val commission = closeHistories.sumOf { it.get("realizedPnL").asDouble() }  // pattern별 commission

            val missed = closeHistories.filter { it["realizedPnL"].doubleValue() == 0.0 }

//            var profits = 0
//            var losses = 0

            missed.forEach { h ->
                val orderSide = h["orderSide"].textValue()
                val entryPrice = h["closeCondition"]["entryPrice"].textValue()
                val lastPrice = h["closeCondition"]["lastPrice"].textValue()

                if (orderSide == "LONG") {
                    if (entryPrice < lastPrice) {
//                        profits++
                        profitMoney += lastPrice.toBigDecimal() - entryPrice.toBigDecimal()
                    } else {
//                        losses++
                        lossMoney += entryPrice.toBigDecimal() - lastPrice.toBigDecimal()
                    }
                } else {
                    if (entryPrice > lastPrice) {
//                        profits++
                        profitMoney += entryPrice.toBigDecimal() - lastPrice.toBigDecimal()
                    } else {
//                        losses++
                        lossMoney += lastPrice.toBigDecimal() - entryPrice.toBigDecimal()
                    }
                }
            }
//            profits to losses
        }
    }
}
