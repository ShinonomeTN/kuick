package com.shinonometn.kuick.base

/**
 * A.K.A CommonResponse
 */
object CR {

    /**
     * Common error representation
     */
    fun error(error: String, message: String = "") = mapOf(
        "error" to error,
        "message" to message
    )

    /**
     * Common error representation
     * if [exception] is BusinessException, return error and message
     * else return full class name with message
     */
    fun error(exception : Throwable) = if(exception is BusinessException) mapOf(
        "error" to exception.error,
        "message" to exception.message
    ) else mapOf(
        "error" to exception::class.qualifiedName,
        "message" to exception.message
    )

    fun success(message: String = "success") = mapOf(
        "message" to message
    )

    /**
     * Common success or fail representation
     * @param boolean true means success otherwise fail
     */
    fun successOrFailed(boolean: Boolean) = mapOf("message" to if (boolean) "success" else "failed")

    /**
     * Common success or fail representation
     *
     * when [any] is:
     * - null: failed
     * - is Exception : failed, with exception, class name and message
     * - is Number: if greater than 0, success, else fail
     * - is Boolean: true means success otherwise fail
     * - Not null: success
     */
    fun successOrFailed(any: Any?) = mapOf(
        "message" to when (any) {
            null -> "failed"

            is Exception -> MicroRPC("failed", "exception", any.javaClass.name, any.message ?: "")

            is Number -> if (any.toDouble() > 0) {
                MicroRPC("success", "count", any.toString())
            } else {
                MicroRPC("failed", "count", any.toString())
            }

            is Boolean -> if(any) MicroRPC("success") else MicroRPC("failed")

            else -> "success"
        }
    )
}