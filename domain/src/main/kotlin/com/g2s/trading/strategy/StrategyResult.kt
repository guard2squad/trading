package com.g2s.trading.strategy

import com.g2s.trading.order.OrderDetail
import com.g2s.trading.position.CloseReferenceData

data class StrategyResult (
    val orderDetail: OrderDetail,
    val closeReferenceData: CloseReferenceData
)
