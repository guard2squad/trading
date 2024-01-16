package com.g2s.trading.util

object ClassMapUtil {
    fun <T : Any> dataClassToLinkedHashMap(dataClassInstance: T): LinkedHashMap<String, Any?> {
        val linkedHashMap = LinkedHashMap<String, Any?>()

        val properties = dataClassInstance::class.java.declaredFields
        properties.forEach { field ->
            field.isAccessible = true
            linkedHashMap[field.name] = field.get(dataClassInstance)
        }
        // TODO(변환시 value 값 String으로)
        return linkedHashMap
    }
}