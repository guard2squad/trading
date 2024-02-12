package com.g2s.trading.exchange

import com.g2s.trading.MarkPrice
import com.g2s.trading.account.Account
import com.g2s.trading.order.Order
import com.g2s.trading.order.Symbol
import com.g2s.trading.position.Position
import com.g2s.trading.position.PositionMode

interface Exchange {
    fun getAccount(): Account
    fun closePosition(position: Position)
    fun openPosition(order: Order)
    fun openPosition(position: Position)
    fun setPositionMode(positionMode: PositionMode)
    fun getMarkPrice(symbol: Symbol): MarkPrice
}
