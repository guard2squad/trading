package com.g2s.trading.exchange

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

// 주문 시 누적 수수료를 추저갛기 위함
@Component
class BinanceCommissionTracker {
    // TODO: transactionTime이 중복될 수 있는지 확인
    private val transactionTimeCommissionMap = ConcurrentHashMap<Long, Double>()

    fun update(transactionTime: Long, commission: Double) {
        transactionTimeCommissionMap.compute(transactionTime) { _, currentCommission ->
            if (currentCommission == null) {
                commission
            } else {
                currentCommission + commission
            }
        }
    }
}
