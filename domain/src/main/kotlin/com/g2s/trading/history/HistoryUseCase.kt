package com.g2s.trading.history

import com.g2s.trading.position.Position
import org.springframework.stereotype.Service

@Service
class HistoryUseCase(
    val conditionUseCase: ConditionUseCase,

) {

    // TODO: History를 관리하는 자료구조 Open, Close 나눠서 관리

    fun recordOpenHistory(position: Position) {
        val openHistory = History.Open(
            position = position,
            strategyKey = position.strategyKey,
            openCondition = conditionUseCase.getOpenCondition(position),
            orderSide = position.orderSide,
            orderType = position.orderType,
            transactionTime = 0,
            commission = 0.0,
            balance = 0.0
        )
        // openCondtiion : openMan에서 알 수 있음 -> ConditionUseCase를 통해 확인
        // ----------------------------- infra
        // transcationTime : synced 후 알 수 있음
        // orderInfo : openMan에서 알 수 있음, synced 후 알 수 있음
        // commission : synced 후 알 수 있음
        // balance : synced 직전 마지막 ACCOUNT_UPDATE에서 알 수 있음
    }
}
