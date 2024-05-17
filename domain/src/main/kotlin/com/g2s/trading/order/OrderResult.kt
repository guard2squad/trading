package com.g2s.trading.order

import com.g2s.trading.symbol.Symbol

data class OrderResult(
    val orderId: String,
    val symbol: Symbol,
    val price: Double,
    val amount: Double,
//    val side: OrderSide,
    // 이거 필요 없을 것 같음. 도메인에서 side 결정하고 NewOrder 만들기 때문에 OrderResult의 side를 쓸 일이 없음, 갱신 되는 것도 아니고
    // 어차피 orderId로 다 식별가능
)
