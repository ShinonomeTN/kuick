package com.shinonometn.kuick.base

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Test
import org.junit.platform.commons.logging.LoggerFactory
import kotlin.reflect.full.createType

internal class ConfigUtilsKtTest {
    private val logger = LoggerFactory.getLogger(ConfigUtilsKtTest::class.java)

    @Test
    fun `Test stringValue`() {
        val values = mapOf(
            "a" to "a",
            "b" to 1,
            "c" to 2.0,
            "d" to true,
            "e" to null,
            "f" to 3L
        )

        val keys = values.keys + "g"

        val config = ConfigFactory.parseMap(values)
        logger.info {
            "Types are : [" +
                values.values.map {
                    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                    if(it != null) it!!::class.createType() else Nothing::class.createType()
                }.joinToString(", ") { it.toString() } +
                    "]"
        }
        val configValues = keys.joinToString("\n") { key ->
            config.getValueOrNull(key)?.let {
                "$key\t: ${it.stringValue()} (${it.valueType().name})"
            } ?: "$key\t: null"
        }
        logger.info { "Values: \n$configValues" }
    }
}