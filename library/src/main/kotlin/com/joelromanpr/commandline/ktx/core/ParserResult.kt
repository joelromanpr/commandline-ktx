package com.joelromanpr.commandline.ktx.core

import com.joelromanpr.commandline.ktx.core.ParseError

public sealed class ParserResult<out T> {
    public data class Parsed<out T>(val value: T) : ParserResult<T>()
    public data class NotParsed<out T>(val errors: List<ParseError>) : ParserResult<T>()

    public fun isSuccess(): Boolean = this is Parsed

    public fun isFailure(): Boolean = this is NotParsed

    public fun getOrNull(): T? = when (this) {
        is Parsed -> value
        is NotParsed -> null
    }

    public fun getOrThrow(): T = when (this) {
        is Parsed -> value
        is NotParsed -> throw IllegalArgumentException(
            "Parsing failed with errors:\n${errors.joinToString("\n") { "  - ${it.message}" }}"
        )
    }
}