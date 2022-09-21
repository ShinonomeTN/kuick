package com.shinonometn.kuick.config

import io.ktor.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class ApplicationConfigurationFactoryTest {
    @Test
    fun `Test load default configuration`() {
        val factory = ApplicationConfigurationFactory()
        val config = factory.build()
        kotlin.test.assertEquals("should equals 1234", "1234", config.tryGetString("ktor.deployment.port"))
    }
}