package com.g2s.trading.history

import com.g2s.trading.position.Position
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ConditionUseCase {

    private val positionOpenConditionMap = ConcurrentHashMap<Position.PositionKey, OpenCondition>()
    private val positionCloseConditionMap =
        ConcurrentHashMap<Position.PositionKey, CloseConditionStruct>()

    fun setOpenCondition(position: Position, condition: OpenCondition) {
        positionOpenConditionMap.computeIfAbsent(position.positionKey) { _ ->
            condition
        }
    }

    // Limit 주문의 경우 익절/손절 closeCondition 각각 저장
    fun setCloseCondition(position: Position, condition: CloseCondition, tradingAction: TradingAction) {
        positionCloseConditionMap.compute(position.positionKey) { _, conditions ->
            when (tradingAction) {
                TradingAction.STOP_LOSS -> {
                    if (conditions == null) {
                        CloseConditionStruct(
                            takeProfit = null,
                            stopLoss = condition
                        )
                    } else {
                        conditions.stopLoss = condition
                        conditions
                    }
                }

                TradingAction.TAKE_PROFIT -> {
                    if (conditions == null) {
                        CloseConditionStruct(
                            takeProfit = condition,
                            stopLoss = null
                        )
                    } else {
                        conditions.takeProfit = condition
                        conditions
                    }
                }
            }
        }
    }

    fun getOpenCondition(position: Position): OpenCondition {
        return positionOpenConditionMap[position.positionKey]!!
    }

    fun getCloseCondition(position: Position): CloseConditionStruct {
        return positionCloseConditionMap[position.positionKey]!!
    }

    fun removeOpenCondition(position: Position) {
        positionOpenConditionMap.remove(position.positionKey)
    }

    fun removeCloseCondition(position: Position) {
        positionCloseConditionMap.remove(position.positionKey)
    }

    data class CloseConditionStruct(
        var takeProfit: CloseCondition?,
        var stopLoss: CloseCondition?,
    )
}
