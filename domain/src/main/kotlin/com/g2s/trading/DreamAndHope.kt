package com.g2s.trading

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

@Service
class DreamAndHope(
    private val exchangeImpl: Exchange
) {
    fun test(){
        val account = exchangeImpl.getAccount()


    }


    @PostConstruct
    fun schedule() {
    }

    fun process() {
        checkCurrentStatus()
        getIndicator()
        closePosition()
        openPosition()
    }

    fun checkCurrentStatus() {
        TODO("로컬, 바낸 상태 sync")
    }

    fun getIndicator() {
        TODO("지표 조회")
    }

    fun closePosition(){
        TODO("현재 포지션 확인")
        TODO("필요시 주문")
    }

    fun openPosition() {
        TODO("전략 적용")
        TODO("필요시 주문")
    }

    fun order() {
        TODO("주문")
        TODO("최종 결과 갱신")
        TODO("거래 기록 저장")
    }
}