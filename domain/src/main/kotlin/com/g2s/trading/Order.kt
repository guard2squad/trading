package com.g2s.trading

sealed class Order {

    class OpenLong : Order()

    class OpenShort : Order()

    class Close: Order()

    object No: Order()
}