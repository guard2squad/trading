package com.g2s.trading.account

data class NewAccount(
    var totalBalance: Double,
    var availableBalance: Double,
    var unSyncedMoney: Double = 0.0
) {
    fun withdraw(amount: Double) {
        totalBalance -= amount
        availableBalance -= amount
    }

    fun deposit(amount: Double) {
        unSyncedMoney = amount
    }

    fun sync() {
        totalBalance += unSyncedMoney
        availableBalance += unSyncedMoney
        unSyncedMoney = 0.0
    }
}