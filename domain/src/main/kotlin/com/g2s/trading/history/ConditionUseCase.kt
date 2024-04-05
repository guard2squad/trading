package com.g2s.trading.history

import com.g2s.trading.position.Position
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ConditionUseCase {

    private val positionConditionMap = ConcurrentHashMap<Position.PositionKey, PositionCondition>()

    fun setOpenCondition(position: Position, condition: OpenCondition) {
        positionConditionMap.computeIfAbsent(position.positionKey) { _ ->
            PositionCondition(openCondition = condition)
        }
    }

    fun setCloseCondition(position: Position, condition: CloseCondition) {
        positionConditionMap.computeIfPresent(position.positionKey) { _, positionCondition ->
            positionCondition.apply {
                positionCondition.closeCondition = condition
            }
        }
    }

    fun getOpenCondition(position: Position): OpenCondition {
        return positionConditionMap.get(position.positionKey)!!.openCondition
    }

    fun getCloseCondition(position: Position): CloseCondition {
        return positionConditionMap.get(position.positionKey)!!.closeCondition!!
    }

    fun removeCondition(position: Position) {
        positionConditionMap.remove(position.positionKey)
    }

    data class PositionCondition(
        val openCondition: OpenCondition,
        var closeCondition: CloseCondition? = null,
    )
}
