package com.g2s.trading.symbol

import com.g2s.trading.exchange.Exchange
import org.springframework.stereotype.Service

@Service
class SymbolUseCase(val exchangeImpl: Exchange) {

    fun getQuantityPrecision(symbol: Symbol): Int {
        return exchangeImpl.getQuantityPrecision(symbol)
    }

    // 시장가 주문일 때만 적용
    // 시장가 주문이 아닐 때 filterType : LOT_SIZE
    fun getMinQty(symbol: Symbol): Double {
        return exchangeImpl.getMinQty(symbol)
    }

    fun getMinNotionalValue(symbol: Symbol): Double {
        return exchangeImpl.getMinNotionalValue(symbol)
    }
}
