package com.joelromanpr.commandline.ktx.core

public sealed class ParseError {
    public abstract val message: String

    public data class UnknownOption(val option: String) : ParseError() {
        override val message: String = "Unknown option: '$option'"
    }

    public data class MissingRequired(val option: String) : ParseError() {
        override val message: String = if (option.startsWith("positional")) {
            "Required $option is missing"
        } else {
            "Required option '--$option' is missing"
        }
    }

    public data class InvalidType(val option: String, val expected: String, val actual: String) : ParseError() {
        override val message: String = "Option '$option' requires a $expected value, but got: '$actual'"
    }

    public data class MissingValue(val option: String) : ParseError() {
        override val message: String = "Option '$option' requires a value"
    }

    public data class ValidationFailed(val option: String, val reason: String) : ParseError() {
        override val message: String = "Validation failed for option '$option': $reason"
    }

    public data class InitializationFailed(val reason: String) : ParseError() {
        override val message: String = reason
    }
}