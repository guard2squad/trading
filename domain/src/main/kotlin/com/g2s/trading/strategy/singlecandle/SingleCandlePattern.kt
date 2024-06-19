package com.g2s.trading.strategy.singlecandle

import java.math.BigDecimal

enum class SingleCandlePattern {
    TOP_TAIL {
        override fun matches(
            tailTop: BigDecimal,
            bodyTop: BigDecimal,
            tailBottom: BigDecimal,
            bodyBottom: BigDecimal
        ): Boolean {
            return tailTop > bodyTop && tailBottom == bodyBottom
        }
    },
    BOTTOM_TAIL {
        override fun matches(
            tailTop: BigDecimal,
            bodyTop: BigDecimal,
            tailBottom: BigDecimal,
            bodyBottom: BigDecimal
        ): Boolean {
            return tailBottom < bodyBottom && tailTop == bodyTop
        }
    },
    MIDDLE_HIGH_TAIL {
        override fun matches(
            tailTop: BigDecimal,
            bodyTop: BigDecimal,
            tailBottom: BigDecimal,
            bodyBottom: BigDecimal
        ): Boolean {
            val highTailLength = tailTop - bodyTop
            val lowTailLength = bodyBottom - tailBottom

            return highTailLength > lowTailLength
        }
    },
    MIDDLE_LOW_TAIL {
        override fun matches(
            tailTop: BigDecimal,
            bodyTop: BigDecimal,
            tailBottom: BigDecimal,
            bodyBottom: BigDecimal
        ): Boolean {
            val highTailLength = tailTop - bodyTop
            val lowTailLength = bodyBottom - tailBottom

            return highTailLength <= lowTailLength
        }
    };

    abstract fun matches(
        tailTop: BigDecimal,
        bodyTop: BigDecimal,
        tailBottom: BigDecimal,
        bodyBottom: BigDecimal
    ): Boolean
}
