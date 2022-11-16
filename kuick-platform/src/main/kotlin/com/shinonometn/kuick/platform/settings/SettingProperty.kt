package com.shinonometn.kuick.platform.settings

import org.apache.commons.codec.binary.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class SettingProperty<T>(
    val name: String,
    val storage: PlatformSettingStorage,
    private val transformer: Transformer<T>) : ReadWriteProperty<Any?, T?> {

    private val cache = AtomicReference<String>()
    private val lock = AtomicInteger(0)

    interface Transformer<T> {
        fun deserialize(string: String?): T?
        fun serialize(value: T?): String?
    }

    fun get(): T? = transformer.deserialize(getString())
    fun set(value: T?) {
        setString(transformer.serialize(value))
    }

    private fun getFromCache(): String? {
        return cache.get() ?: run {
            val lock = this.lock.getAndIncrement()
            try {
                val value = storage.get(name)
                if (this.lock.get() != lock) return cache.get()
                cache.set(value)
                value
            } finally {
                this.lock.decrementAndGet()
            }
        }
    }

    private fun setToCache(value: String?) {
        this.lock.getAndIncrement()
        try {
            cache.set(value)
            storage.set(name, value)
        } finally {
            this.lock.decrementAndGet()
        }
    }

    fun getString() = getFromCache()
    fun setString(string: String?) = setToCache(string)

    object Transformers {
        val String = object : Transformer<String> {
            override fun deserialize(string: String?): String? = string
            override fun serialize(value: String?): String? = value
        }
        val Int = object : Transformer<Int> {
            override fun serialize(value: Int?): String? = value?.toString()
            override fun deserialize(string: String?): Int? = string?.toIntOrNull()
        }
        val Double = object : Transformer<Double> {
            override fun serialize(value: Double?): String? = value?.toString()
            override fun deserialize(string: String?): Double? = string?.toDoubleOrNull()
        }
        val Long = object : Transformer<Long> {
            override fun serialize(value: Long?): String? = value?.toString()
            override fun deserialize(string: String?): Long? = string?.toLongOrNull()
        }
        val Boolean = object : Transformer<Boolean> {
            override fun serialize(value: Boolean?): String? = value?.toString()
            override fun deserialize(string: String?): Boolean? = string?.toBoolean()
        }
        val Binary = object : Transformer<ByteArray> {
            override fun serialize(value: ByteArray?): String? = Base64.encodeBase64String(value ?: ByteArray(0))
            override fun deserialize(string: String?): ByteArray? = string?.let { Base64.decodeBase64(it) }
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = get()
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) = set(value)
}