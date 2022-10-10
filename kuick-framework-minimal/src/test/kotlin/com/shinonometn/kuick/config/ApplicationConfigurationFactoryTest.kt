package com.shinonometn.kuick.config

import io.ktor.config.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplicationConfigurationFactoryTest {
    @Test
    fun `Test load default configuration`() {
        val factory = ApplicationConfigurationFactory.create()
        val config = factory.build()
        assertEquals("0", config.tryGetString("ktor.deployment.port"), "should equals 0")
    }

    @Test
    fun `Test load profile`() {
        val factory = ApplicationConfigurationFactory.create(arrayOf(
            "-profile=dev",
            "-config=classpath://application-dev.conf",
            "-config=classpath://application-prod.conf"
        ))
        val config = factory.build()
        assertEquals("1", config.tryGetString("ktor.deployment.port"), "should equals 1")
    }

    @Test
    fun `Test load from file`() {
        val factory = ApplicationConfigurationFactory.create(arrayOf(
            "-profile=prod",
            "-config=file://./src/test/resources/application-dev.conf",
            "-config=file://./src/test/resources/application-prod.conf",
        ))
        val config = factory.build()
        assertEquals("2", config.tryGetString("ktor.deployment.port"), "should equals 2")
    }

    @Test
    fun `Test load from cmd line`() {
        val factory = ApplicationConfigurationFactory.create(arrayOf(
            "-profile=dev",
            "-config=classpath://application-dev.conf",
            "-P:ktor.deployment.port=-1"
        ))
        val config = factory.build()
        assertEquals("-1", config.tryGetString("ktor.deployment.port"), "should equals 2")
    }
}