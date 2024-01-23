package com.g2s.trading.strategy

import com.g2s.trading.order.OrderDetail
import com.g2s.trading.position.LiquidationData

data class StrategyResult (
    val orderDetail: OrderDetail,
    val liquidationData: LiquidationData
)
