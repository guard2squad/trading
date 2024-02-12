package com.g2s.trading.dreamandhope

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component


@Component
class DreamAndHopeInitializer(
    private val dreamAndHope: DreamAndHope
) : ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
//        dreamAndHope.init()
    }
}