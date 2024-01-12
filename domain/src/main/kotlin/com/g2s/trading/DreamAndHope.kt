package com.g2s.trading

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

@Service
class DreamAndHope(
    private val exchangeImpl: Exchange
) {
    fun test() : String {

        // 직전 해머 패턴 보고 포지션 변동

        // 계좌 조회
        val account = exchangeImpl.getAccount()

        // 지표 조회
        val indicators = exchangeImpl.getIndicators()

        // 포지션 조회
        val positon = exchangeImpl.getPosition()

//        // condition 생성
//        val closeCondition = exchangeImpl.getCondition("")
//
//        // close position
//        val hasPositionClosed = exchangeImpl.closePosition(positon, indicators, closeCondition)
//
//        // condition 생성
//        val openCondition = exchangeImpl.getCondition("")
//
//        // open position(주문)
//        val hasPositionOpened = exchangeImpl.openPosition(account, indicators, openCondition)

        exchangeImpl.openTest()

        exchangeImpl.closeTest()

        return "Not yet implemented"
    }


    @PostConstruct
    fun schedule() {
    }

//    fun process() {
//        checkCurrentStatus()
//        getIndicator()
//        closePosition()
//        openPosition()
//    }
}