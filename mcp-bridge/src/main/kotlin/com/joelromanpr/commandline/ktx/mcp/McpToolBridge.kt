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
package com.joelromanpr.commandline.ktx.mcp

import com.joelromanpr.commandline.ktx.Parser
import com.joelromanpr.commandline.ktx.core.ParseError
import com.joelromanpr.commandline.ktx.core.ParserResult
import kotlin.reflect.KClass

/**
 * A protocol-neutral bridge between a command definition and an MCP tool handler.
 *
 * The host application owns the MCP server, authorization, output serialization, and
 * confirmation of actions with side effects. This class never runs a shell command.
 */
public class McpToolBridge<T : Any>(
    public val name: String,
    public val description: String,
    private val inputSchema: Map<String, Any?>,
    private val parse: (Map<String, Any?>) -> ParserResult<T>
) {
    init {
        require(name.length in 1..128 && name.all { it.isLetterOrDigit() && it.isAscii() || it == '_' || it == '-' || it == '.' }) {
            "MCP tool names must contain 1 to 128 ASCII letters, digits, underscores, hyphens, or dots"
        }
        require(description.isNotBlank()) { "MCP tool descriptions must not be blank" }
        require(inputSchema["type"] == "object") { "MCP tool input schemas must have an object root" }
    }

    /** Returns fields for an MCP tool descriptor, ready for a host SDK to serialize. */
    public fun descriptor(): Map<String, Any?> = linkedMapOf(
        "name" to name,
        "description" to description,
        "inputSchema" to inputSchema
    )

    /**
     * Validates structured arguments with the library parser before calling [handler].
     * Handler exceptions remain the host application's responsibility.
     */
    public fun <R> invoke(
        arguments: Map<String, Any?>,
        handler: (T) -> R
    ): McpInvocationResult<R> = when (val result = parse(arguments)) {
        is ParserResult.Parsed -> McpInvocationResult.Success(handler(result.value))
        is ParserResult.NotParsed -> McpInvocationResult.InvalidArguments(result.errors.map(ParseError::toMcpInputError))
    }
}

/** Builds an MCP bridge from the parser's command schema and structured validator. */
public inline fun <reified T : Any> Parser.mcpTool(
    name: String,
    description: String,
    customTypeSchemas: Map<KClass<*>, Map<String, Any?>> = emptyMap()
): McpToolBridge<T> = McpToolBridge(
    name = name,
    description = description,
    inputSchema = describe<T>().toJsonSchema(customTypeSchemas),
    parse = { arguments -> parseStructured<T>(arguments) }
)

/** A validated tool call or recoverable argument errors. */
public sealed class McpInvocationResult<out R> {
    public data class Success<out R>(val value: R) : McpInvocationResult<R>()

    public data class InvalidArguments(val errors: List<McpInputError>) : McpInvocationResult<Nothing>() {
        /** MCP `tools/call` result fields for a recoverable tool error. */
        public fun asMcpToolResult(): Map<String, Any?> {
            val structuredContent = linkedMapOf("errors" to errors.map { it.toMap() })
            val text = "{\"errors\":[${errors.joinToString(",") { it.toJson() }}]}"
            return linkedMapOf(
                "resultType" to "complete",
                "content" to listOf(linkedMapOf("type" to "text", "text" to text)),
                "structuredContent" to structuredContent,
                "isError" to true
            )
        }
    }
}

/** Error fields suitable for returning to a model without echoing supplied values. */
public data class McpInputError(
    val code: String,
    val message: String,
    val field: String?,
    val tokenIndex: Int?
) {
    public fun toMap(): Map<String, Any> = linkedMapOf<String, Any>(
        "code" to code,
        "message" to message
    ).apply {
        field?.let { put("field", it) }
        tokenIndex?.let { put("tokenIndex", it) }
    }

    internal fun toJson(): String = buildString {
        append("{\"code\":")
        append(code.jsonQuoted())
        append(",\"message\":")
        append(message.jsonQuoted())
        field?.let {
            append(",\"field\":")
            append(it.jsonQuoted())
        }
        tokenIndex?.let {
            append(",\"tokenIndex\":")
            append(it)
        }
        append('}')
    }
}

private fun Char.isAscii(): Boolean = code <= 0x7f

private fun String.jsonQuoted(): String = buildString {
    append('"')
    for (character in this@jsonQuoted) {
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

private fun ParseError.toMcpInputError(): McpInputError {
    val safeMessage = when (this) {
        is ParseError.UnknownOption -> "Unknown argument"
        is ParseError.MissingRequired -> "Required argument is missing"
        is ParseError.InvalidType -> "Expected a value of type $expected"
        is ParseError.MissingValue -> "Argument requires a value"
        is ParseError.ValidationFailed -> "Argument failed validation"
        is ParseError.InitializationFailed -> "Command could not be initialized"
    }
    val safeField = if (this is ParseError.UnknownOption) null else field
    return McpInputError(code = code, message = safeMessage, field = safeField, tokenIndex = tokenIndex)
}
