package com.g2s.trading.history

import com.g2s.trading.strategy.minimum_simple.SimplePattern
import java.math.BigDecimal

/*
 각 오픈 조건 클래스의 이름은 해당 전략의 이름과 'Condition'을 결합하여 명명한다.
 예를 들어, 'Simple' 전략의 경우 조건 클래스의 이름은 'SimpleCondition'이 된다.

 - 전략 이름: 전략을 나타내는 명확한 이름으로 StrategyType enum의 value (예: Simple)
 - Condition: 'Condition' 고정 문자열을 접미사로 사용하여 클래스가 특정 전략의 조건을 정의함을 나타냄
*/
sealed class OpenCondition {
    data class SimpleCondition(
        val patten: SimplePattern,
        val candleHammerRatio: BigDecimal,
        val operationalCandleHammerRatio: BigDecimal,
        val beforeBalance: Double
    ) : OpenCondition()

    data object ManualCondition : OpenCondition()
}
