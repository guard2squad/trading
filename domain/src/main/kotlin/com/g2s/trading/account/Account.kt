package com.g2s.trading.account

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

data class Account(
    var totalBalance: BigDecimal,
    var availableBalance: BigDecimal,
    var unavailableBalance: BigDecimal = BigDecimal.ZERO,
    var unSyncedMoney: BigDecimal = BigDecimal.ZERO,
) {
    private val decimalFormat = DecimalFormat("#.##").apply { roundingMode = RoundingMode.HALF_EVEN }

    fun withdraw(amount: BigDecimal) {
        availableBalance -= amount
    }

    @Synchronized
    fun deposit(amount: BigDecimal) {
        unSyncedMoney += amount
    }

    @Synchronized
    fun transfer(amount: BigDecimal) {
        availableBalance -= amount
        unavailableBalance += amount
    }

    fun sync() {
        availableBalance += unSyncedMoney
        totalBalance = availableBalance + unavailableBalance
        unSyncedMoney = BigDecimal.ZERO
    }

    override fun toString(): String {
        return "Account(totalBalance=${format(totalBalance)}, " +
                "availableBalance=${format(availableBalance)}, " +
                "unavailableBalance=${format(unavailableBalance)}, " +
                "unSyncedMoney=${format(unSyncedMoney)})"
    }

    private fun format(amount: BigDecimal): String {
        return decimalFormat.format(amount)
    }
}