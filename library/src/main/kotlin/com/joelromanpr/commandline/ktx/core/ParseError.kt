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

public sealed class ParseError {
    public abstract val message: String
    /** Stable identifier for machine-readable diagnostics. */
    public abstract val code: String
    /** The option or field involved, when one is known. */
    public open val field: String? = null
    /** Zero-based argument token index, when the input came from argv. */
    public var tokenIndex: Int? = null
        private set

    internal fun atToken(index: Int): ParseError {
        tokenIndex = index
        return this
    }

    public data class UnknownOption(val option: String) : ParseError() {
        override val code: String = "UNKNOWN_OPTION"
        override val field: String = option

        override val message: String = "Unknown option: '$option'"
    }

    public data class MissingRequired(val option: String) : ParseError() {
        override val code: String = "MISSING_REQUIRED"
        override val field: String = option

        override val message: String = "Required input '$option' is missing"
    }

    public data class InvalidType(val option: String, val expected: String, val actual: String) : ParseError() {
        override val code: String = "INVALID_TYPE"
        override val field: String = option.removePrefix("--").removePrefix("-")

        override val message: String = "Option '$option' requires a $expected value, but got: '$actual'"
    }

    public data class MissingValue(val option: String) : ParseError() {
        override val code: String = "MISSING_VALUE"
        override val field: String = option.removePrefix("--").removePrefix("-")

        override val message: String = "Option '$option' requires a value"
    }

    public data class ValidationFailed(val option: String, val reason: String) : ParseError() {
        override val code: String = "VALIDATION_FAILED"
        override val field: String = option.removePrefix("--").removePrefix("-")

        override val message: String = "Validation failed for option '$option': $reason"
    }

    public data class InitializationFailed(val reason: String) : ParseError() {
        override val code: String = "INITIALIZATION_FAILED"

        override val message: String = reason
    }
}
