package com.g2s.trading.account

import com.g2s.trading.Exchange
import com.g2s.trading.Symbol
import org.springframework.stereotype.Service
import java.lang.RuntimeException
import java.math.BigDecimal

@Service
class AccountUseCase(
    private val exchangeImpl: Exchange
) {
    lateinit var account: Account
    private val availableRatioMap: Map<String, Double> = mapOf(
        "simple" to 0.25
    )

    fun syncAccount() {
        val account = exchangeImpl.getAccount()
        this.account = account
    }

    fun getAccount(): Account {
        syncAccount()
        return account
    }

    fun getPosition(symbol: Symbol): Position? {
        return account.positions.firstOrNull { it.symbol == symbol }
    }

    fun getPositions(symbols: List<Symbol>): List<Position> {
        return account.positions.filter { symbols.contains(it.symbol) }
    }

    fun getAllPositions(): List<Position> {
        return account.positions
    }

    fun closePosition(position: Position) {
        exchangeImpl.closePosition(position)
    }

    fun closePositions(positions: List<Position>) {
        positions.forEach { closePosition(it) }
    }

    fun getAvailableBalance(strategyKey: String, asset: Asset): BigDecimal {
        val availableRatio = availableRatioMap.get(strategyKey)
        if (availableRatio == null) throw RuntimeException("available ratio not found")

        val assetWallet = account.assetWallets.first { it.asset == asset }

        return BigDecimal(assetWallet.walletBalance).multiply(BigDecimal(availableRatio))
    }
}