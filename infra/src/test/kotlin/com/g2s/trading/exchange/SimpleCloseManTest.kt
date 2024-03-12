package com.g2s.trading.exchange

import com.g2s.trading.MarkPriceUseCase
import com.g2s.trading.common.ObjectMapperProvider
import com.g2s.trading.order.OrderSide
import com.g2s.trading.order.OrderType
import com.g2s.trading.position.Position
import com.g2s.trading.symbol.Symbol
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

@ActiveProfiles("TEST")
@SpringBootTest(classes = [TestConfig::class])
class SimpleCloseManTest(
    @Autowired private val markPriceUseCase: MarkPriceUseCase
) {

    private val om = ObjectMapperProvider.get()

    @Test
    fun closeTest() {
        val position = Position(
            strategyKey = "test",
            symbol = Symbol.BTCUSDT,
            orderSide = OrderSide.SHORT,
            orderType = OrderType.MARKET,
            entryPrice = 69762.3,
            positionAmt = -0.002,
            // 직접 입력 필요
            referenceData = om.readTree(
                "{\n" +
                        "    \"symbol\": \"BTCUSDT\",\n" +
                        "    \"interval\": \"ONE_MINUTE\",\n" +
                        "    \"key\": 1710087300000,\n" +
                        "    \"open\": 69488,\n" +
                        "    \"high\": 69820,\n" +
                        "    \"low\": 69338.2,\n" +
                        "    \"close\": 69499.5,\n" +
                        "    \"volume\": 40.476,\n" +
                        "    \"numberOfTrades\": 78,\n" +
                        "    \"tailLength\": 480.75\n" +
                        "}"
            )
        )

        println(shouldClose(position))
    }

    @Test
    fun getLastPriceTest() {
        println(markPriceUseCase.getMarkPrice(Symbol.BTCUSDT))
    }

    private fun shouldClose(position: Position): Boolean {
        val stopLossFactor = BigDecimal(1.2)
        val entryPrice = BigDecimal(position.entryPrice)
        val lastPrice = BigDecimal(markPriceUseCase.getMarkPrice(position.symbol).price)
        when (position.orderSide) {
            OrderSide.LONG -> {
                // 손절
                val stickLength = BigDecimal(position.referenceData["high"].asDouble()).minus(BigDecimal(position.referenceData["low"].asDouble()))
                if (stickLength.multiply(stopLossFactor) > entryPrice.minus(lastPrice)) {
                    println(
                        "롱 손절: lastPrice: $lastPrice, 오픈시 고가 - 저가: $stickLength" +
                                ", StopLossFactor 반영 후 고가 - 저가: ${stickLength.multiply(stopLossFactor)}"
                    )
                    return true
                }
                // 익절
                if (lastPrice > entryPrice.plus(BigDecimal(position.referenceData["tailLength"].asDouble()))) {
                    println(
                        "롱 익절: lastPrice: $lastPrice, entryPrice: $entryPrice, 오픈시 꼬리 길이: ${position.referenceData["tailLength"].asDouble()}"
                    )
                    return true
                }
            }

            OrderSide.SHORT -> {
                // 손절
                val stickLength = BigDecimal(position.referenceData["high"].asDouble()).minus(BigDecimal(position.referenceData["low"].asDouble()))
                if (stickLength.multiply(stopLossFactor) < lastPrice.minus(entryPrice)) {
                    println(
                        "숏 손절: lastPrice: $lastPrice, 오픈시 고가 - 저가: $stickLength" +
                                ", StopLossFactor 반영 후 고가 - 저가: ${stickLength.multiply(stopLossFactor)}"
                    )
                    return true
                }
                // 익절
                if (lastPrice < entryPrice.minus(BigDecimal(position.referenceData["tailLength"].asDouble()))) {
                    println(
                        "숏 익절: lastPrice: $lastPrice, entryPrice: $entryPrice, 오픈시 꼬리 길이: ${position.referenceData["tailLength"].asDouble()}"
                    )
                    return true;
                }
            }
        }
        return false
    }
}
