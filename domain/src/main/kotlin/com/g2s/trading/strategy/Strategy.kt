package com.g2s.trading.strategy

import com.g2s.trading.order.OrderDetail

interface Strategy<T : StrategySpec> {
    fun setSpec(strategySpec: T)
    fun invoke() : OrderDetail?
}
