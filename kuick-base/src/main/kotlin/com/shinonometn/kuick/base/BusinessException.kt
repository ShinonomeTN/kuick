@file:Suppress("NOTHING_TO_INLINE")

package com.shinonometn.kuick.base

/**
 * Represent logical error in business flows
 */
open class BusinessException(val error: String = "business_error", message: String) : Exception(message)

inline fun businessError(message: String): Nothing = throw BusinessException(message = message)

inline fun validationError(message: String): Nothing = throw BusinessException("validation_error", message)

inline fun unexpectedError(message: String): Nothing = throw BusinessException("unexpected_error", message)