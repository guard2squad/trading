package com.g2s.trading.account

import java.math.BigDecimal

data class Account(
    var totalBalance: BigDecimal,
    var availableBalance: BigDecimal,
    var unavailableBalance: BigDecimal = BigDecimal.ZERO,
    var unSyncedMoney: BigDecimal = BigDecimal.ZERO,
) {

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
}