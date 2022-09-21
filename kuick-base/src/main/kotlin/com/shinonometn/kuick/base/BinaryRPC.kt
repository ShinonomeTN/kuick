package com.shinonometn.kuick.base

import com.shinonometn.koemans.utils.toByteArray
import com.shinonometn.koemans.utils.toInt
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

object BinaryRPC {

    fun create(builder: EncoderContext.() -> Unit): DataProducer = DataProducerImpl(EncoderContext().apply(builder).blocks)

    interface DataProducer {
        fun writeHeaders(bufferFactory: () -> ByteArrayOutputStream): Int
        fun writeBlocks(bufferFactory: () -> ByteArrayOutputStream): Int
    }

    private class DataProducerImpl(private val blocks: List<Pair<Meta, ByteArray>>) : DataProducer {
        override fun writeHeaders(bufferFactory: () -> ByteArrayOutputStream): Int {
            val buffer = bufferFactory()
            return blocks.sumOf { val bytes = it.first.toBytes(); buffer.write(bytes); bytes.size }
        }

        override fun writeBlocks(bufferFactory: () -> ByteArrayOutputStream): Int {
            val buffer = bufferFactory()
            return blocks.sumOf { buffer.write(it.second); it.second.size }
        }
    }

    class EncoderContext internal constructor() {
        internal val blocks = mutableListOf<Pair<Meta, ByteArray>>()

        fun boolean(bool: Boolean) {
            blocks.add(Meta(Meta.Type.Boolean, size = 0, notation = if (bool) NOTATION_BOOLEAN_TRUE else 0) to EMPTY_BYTEARRAY)
        }

        fun string(str: String) {
            val byteArray = str.toByteArray()
            blocks.add(Meta(Meta.Type.String, size = byteArray.size) to byteArray)
        }

        fun integer(int: Int) {
            val byteArray = int.toByteArray()
            blocks.add(Meta(Meta.Type.Integer, size = byteArray.size) to byteArray)
        }

        fun decimal(decimal: BigDecimal) {
            val precision = toShortUIntBytes(decimal.precision().toLong())
            val integer = decimal.toBigIntegerExact().toByteArray()
            val byteArray = precision + integer
            blocks.add(Meta(Meta.Type.Decimal, size = precision.size + integer.size, notation = precision.size) to byteArray)
        }

        fun binary(bytes: ByteArray) {
            blocks.add(Meta(Meta.Type.Binary, size = bytes.size) to bytes)
        }
    }

    /**
     * Binary field tag
     * Encoding:
     * ```
     * 000_0 0_000 0000 0000 ....
     * 1   2 3 4   5         var-length-field 1
     * ```
     * fixed fields:
     * 1. type
     * 2. nullable
     * 3. is null
     * 4. size byte count
     * 5. depend on type
     *
     * var-length-fields:
     * 1. size
     */
    class Meta internal constructor(
        val type: Type,
        val nullable: Boolean = false,
        val isNull: Boolean = false,
        val size: Int,
        val notation: Int = 0
    ) {
        enum class Type { Boolean, Integer, Decimal, String, Binary }

        fun toBytes(): ByteArray {
            var buffer: Int = (type.ordinal and 0x7) shl 5
            if (nullable) buffer = buffer or 0xF000
            if (isNull) {
                buffer = buffer or 0xF800
                return buffer.toByteArray()
            }

            val sizeBytes = toShortUIntBytes(size.toLong())
            buffer = buffer or ((sizeBytes.size and 0x7) shl 1.bytes)
            buffer = buffer or (notation and 0xFF)

            return buffer.toByteArray() + sizeBytes
        }

        companion object {
            fun string(size: Int, notation: Int) = Meta(Type.String, false, false, size, notation)

            fun fromBytes(bytes: ByteArray): Meta {
                require(bytes.size >= 2) { "meta byte insufficient" }
                val buffer: Int = ((bytes[0].toUByte().toInt() and 0xFF) shl 8) or ((bytes[1].toUByte().toInt() and 0xFF))

                val type = Type.values()[(buffer ushr (1.bytes + 5)) and 0x7]
                val nullable = ((buffer ushr (1.bytes + 4)) and 0x1) > 0
                val isNull = ((buffer ushr (1.bytes + 3) and 0x1)) > 0
                val sizeByteCount = ((buffer ushr 1.bytes) and 0x7)
                val notation = buffer and 0xFF

                require((bytes.size - 2) >= sizeByteCount) { "invalid size and offset info" }
                val size = if (sizeByteCount > 0) bytes.toInt(2, sizeByteCount) else 0

                return Meta(type, nullable, isNull, size, notation)
            }
        }
    }

    internal fun toShortUIntBytes(length: Long): ByteArray {
        require(length > 0) { "length should greater than zero" }
        var bytes = length
        var written = 0
        val buffer = ByteArray(Long.SIZE_BYTES)
        while (bytes != 0L) {
            val byte = (bytes ushr 7.bytes) and 0xFF
            bytes = bytes shl 1.bytes
            if (byte == 0L) continue
            buffer[written++] = byte.toByte()
        }
        return buffer.copyOf(written)
    }

    private val EMPTY_BYTEARRAY = ByteArray(0)

    private const val NOTATION_BOOLEAN_TRUE = 0b1000_0000
}

/** as byte unit (same as times 8) */
private val Int.bytes: Int
    get() = times(8)