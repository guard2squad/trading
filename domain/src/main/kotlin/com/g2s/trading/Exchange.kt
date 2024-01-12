package com.g2s.trading

interface Exchange {

    fun getAccount(): Account

    fun getIndicators(): Indicator

    fun getPosition(): Position

    fun closePosition(position: Position, indicator: Indicator, condition: Condition): Boolean

    fun openPosition(account: Account, indicator: Indicator, condition: Condition): Boolean

    fun getCondition(jsonString: String): Condition

    fun openTest()

    fun closeTest()
}