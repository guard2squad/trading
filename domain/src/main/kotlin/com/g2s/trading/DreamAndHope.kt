package com.g2s.trading

import com.g2s.trading.openman.OpenMan
import org.springframework.stereotype.Service

@Service
class DreamAndHope(
    private val simpleOpenMan: OpenMan
) {

    fun test() {
        simpleOpenMan.open()
    }

}
