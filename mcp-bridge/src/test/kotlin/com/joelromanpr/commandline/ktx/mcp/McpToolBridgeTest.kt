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
import com.joelromanpr.commandline.ktx.annotations.Option
import com.joelromanpr.commandline.ktx.core.ParseError
import com.joelromanpr.commandline.ktx.core.ParserResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class McpToolBridgeTest {
    class CountInput {
        @Option(longName = "count", required = true, helpText = "Number of items")
        var count: Int = 0
    }

    @Test
    fun `descriptor uses a stable MCP tool shape from the command schema`() {
        val bridge = Parser.Default.mcpTool<CountInput>("count_items", "Count items")
        val descriptor = bridge.descriptor()

        assertEquals(listOf("name", "description", "inputSchema"), descriptor.keys.toList())
        assertEquals("count_items", descriptor["name"])
        assertEquals("Count items", descriptor["description"])
        val schema = assertIs<Map<*, *>>(descriptor["inputSchema"])
        assertEquals("object", schema["type"])
        assertTrue("count" in assertIs<Map<*, *>>(schema["properties"]))
        assertTrue("count" in assertIs<List<*>>(schema["required"]))
    }

    @Test
    fun `valid structured arguments reach the handler`() {
        val bridge = Parser.Default.mcpTool<CountInput>("count_items", "Count items")
        val result = bridge.invoke(mapOf("count" to 3)) { input -> input.count * 2 }

        assertEquals(6, assertIs<McpInvocationResult.Success<*>>(result).value)
    }

    @Test
    fun `invalid arguments do not call the handler or echo supplied values`() {
        val secret = "private-credential"
        val bridge = McpToolBridge<CountInput>(
            name = "count_items",
            description = "Count items",
            inputSchema = mapOf("type" to "object"),
            parse = {
                ParserResult.NotParsed(
                    listOf(ParseError.InvalidType("count", "Int", secret), ParseError.UnknownOption(secret))
                )
            }
        )
        var handlerCalled = false

        val result = bridge.invoke(mapOf("count" to secret)) {
            handlerCalled = true
            it
        }

        assertFalse(handlerCalled)
        val invalid = assertIs<McpInvocationResult.InvalidArguments>(result)
        val mcpResult = invalid.asMcpToolResult()
        assertEquals("complete", mcpResult["resultType"])
        assertEquals(true, mcpResult["isError"])
        assertFalse(mcpResult.toString().contains(secret))
        val errors = assertIs<List<*>>(assertIs<Map<*, *>>(mcpResult["structuredContent"])["errors"])
        val firstError = assertIs<Map<*, *>>(errors.first())
        assertTrue(assertIs<String>(firstError["code"]).isNotBlank())
        assertEquals("count", firstError["field"])
        assertFalse(assertIs<Map<*, *>>(errors.last()).containsKey("field"))
    }

    @Test
    fun `structured errors are mirrored as escaped JSON text`() {
        val invalid = McpInvocationResult.InvalidArguments(
            listOf(McpInputError("BAD", "line\n\"quote\"", "count", null))
        )
        val result = invalid.asMcpToolResult()
        val content = assertIs<List<*>>(result["content"])
        val text = assertIs<String>(assertIs<Map<*, *>>(content.single())["text"])

        assertEquals("{\"errors\":[{\"code\":\"BAD\",\"message\":\"line\\n\\\"quote\\\"\",\"field\":\"count\"}]}", text)
    }

    @Test
    fun `invalid tool names are rejected before registration`() {
        assertFailsWith<IllegalArgumentException> {
            Parser.Default.mcpTool<CountInput>("count items", "Count items")
        }
    }
}
