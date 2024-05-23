package com.g2s.trading.order

import com.g2s.trading.common.ApiErrors

enum class OrderFailErrors : ApiErrors {
    CLIENT_ERROR,
    SERVER_ERROR,
    CONNECTOR_ERROR,
    ORDER_WOULD_IMMEDIATELY_TRIGGER,
}
