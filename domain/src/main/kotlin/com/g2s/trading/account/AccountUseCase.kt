package com.g2s.trading.account

import com.g2s.trading.exchange.Exchange
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class AccountUseCase(
    private val exchangeImpl: Exchange
) {
    private lateinit var localAccount: Account

    init {
        localAccount = loadAccount()
    }

    @Synchronized
    fun withdraw(positionNotionalValue: BigDecimal, commission: BigDecimal): Money {
        localAccount.sync()
        if (positionNotionalValue > localAccount.availableBalance) {
            return Money.NotAvailableMoney("예상 포지션 명목 가치 > 사용 가능 금액")
        }
        if (commission > localAccount.availableBalance) {
            return Money.NotAvailableMoney("수수료 > 사용 가능 금액")
        }
        localAccount.transfer(positionNotionalValue)
        localAccount.withdraw(commission)

        return Money.AvailableMoney(positionNotionalValue, commission)
    }

    @Synchronized
    fun undoWithdrawal(money: Money.AvailableMoney) {
        localAccount.transfer(-money.positionAmount)
        localAccount.deposit(money.fee)
    }

    fun deposit(amount: BigDecimal) {
        localAccount.deposit(amount)
    }

    // unavailable -> available
    fun reverseTransfer(amount: BigDecimal) {
        localAccount.transfer(-amount)
    }

    fun getAccount(): Account {
        return localAccount
    }

    private fun loadAccount(): Account {
        return exchangeImpl.getAccount()
    }
}