/*
 * Copyright (C) 2025 joelromanpr (Joel Roman)
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
