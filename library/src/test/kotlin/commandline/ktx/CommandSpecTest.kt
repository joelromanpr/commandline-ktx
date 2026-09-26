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
package com.joelromanpr.commandline.ktx

import com.joelromanpr.commandline.ktx.annotations.Option
import com.joelromanpr.commandline.ktx.annotations.OptionGroup
import com.joelromanpr.commandline.ktx.annotations.Range
import com.joelromanpr.commandline.ktx.annotations.Value
import com.joelromanpr.commandline.ktx.converters.TypeConverter
import com.joelromanpr.commandline.ktx.core.ParseError
import com.joelromanpr.commandline.ktx.core.ParserResult
import com.joelromanpr.commandline.ktx.core.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CommandSpecTest {
    @OptionGroup("input", required = true)
    data class Input(
        @Option(longName = "text", group = "input") var text: String? = null,
        @Value(index = 0, group = "input") var file: String? = null,
        @Option(longName = "count", required = true, default = "2") @Range(min = 1, max = 10)
        var count: Int = 0,
        @Option(longName = "tags") var tags: List<String> = emptyList(),
        @Option(longName = "verbose") var verbose: Boolean = true
    )

    @Test
    fun `schema is deterministic and describes types ranges defaults and groups`() {
        val spec = Parser.Default.describe<Input>()
        assertEquals(listOf("count", "tags", "text", "verbose"), spec.options.map { it.name })
        assertEquals(listOf("file", "text"), spec.groups.single().members)

        val schema = spec.toJsonSchema()
        assertEquals(schema, Parser.Default.describe<Input>().toJsonSchema())
        assertEquals("object", schema["type"])
        assertEquals(false, schema["additionalProperties"])
        val fields = assertIs<Map<*, *>>(schema["properties"])
        val count = assertIs<Map<*, *>>(fields["count"])
        assertEquals("integer", count["type"])
        assertEquals(1, count["minimum"])
        assertEquals(10, count["maximum"])
        assertEquals("array", assertIs<Map<*, *>>(fields["tags"])["type"])
        assertEquals("boolean", assertIs<Map<*, *>>(fields["verbose"])["type"])
        assertEquals(null, schema["required"])
        val groupRules = assertIs<List<*>>(schema["allOf"])
        assertTrue(groupRules.isNotEmpty())
    }

    @Test
    fun `structured values preserve typed lists and explicit false`() {
        val result = Parser.Default.parseStructured<Input>(
            mapOf("text" to "hello", "tags" to listOf("a,b", "c"), "verbose" to false)
        )
        val parsed = assertIs<ParserResult.Parsed<Input>>(result)
        assertEquals("hello", parsed.value.text)
        assertEquals(listOf("a,b", "c"), parsed.value.tags)
        assertEquals(false, parsed.value.verbose)
        assertEquals(2, parsed.value.count)
        assertEquals(ValueSource.STRUCTURED, parsed.sources["text"])
        assertEquals(ValueSource.ANNOTATION_DEFAULT, parsed.sources["count"])
    }

    @Test
    fun `structured input rejects unknown fields and conflicting group members`() {
        val result = Parser.Default.parseStructured<Input>(
            mapOf("text" to "hello", "file" to "in.txt", "typo" to 1)
        )
        val errors = assertIs<ParserResult.NotParsed<Input>>(result).errors
        assertTrue(errors.any { it.code == "UNKNOWN_OPTION" && it.field == "typo" })
        assertTrue(errors.any { it.code == "VALIDATION_FAILED" && it.field == "input" })
    }

    @Test
    fun `sensitive structured values are redacted from errors`() {
        data class Secret(@Option(longName = "token", sensitive = true) var token: Int = 0)
        val result = Parser.Default.parseStructured<Secret>(mapOf("token" to "my-secret"))
        val error = assertIs<ParseError.InvalidType>(assertIs<ParserResult.NotParsed<Secret>>(result).errors.single())
        assertEquals("<redacted>", error.actual)
        assertTrue(!error.message.contains("my-secret"))
    }

    @Test
    fun `custom types need an explicit schema instead of a guessed type`() {
        data class Address(val value: String)
        data class Custom(@Option(longName = "address") var address: Address? = null)
        val spec = Parser.Default.describe<Custom>()
        assertFailsWith<IllegalArgumentException> { spec.toJsonSchema() }
        assertFailsWith<IllegalArgumentException> {
            spec.toJsonSchema(mapOf(Address::class to mapOf("type" to "object")))
        }
        val schema = spec.toJsonSchema(mapOf(Address::class to mapOf("type" to "string", "format" to "uri")))
        assertEquals("string", assertIs<Map<*, *>>(assertIs<Map<*, *>>(schema["properties"])["address"])["type"])
    }

    @Test
    fun `explicit group input suppresses another members annotation default`() {
        @OptionGroup("input")
        data class Choice(
            @Option(longName = "text", group = "input", default = "fallback") var text: String? = null,
            @Option(longName = "file", group = "input") var file: String? = null
        )
        val fromCli = assertIs<ParserResult.Parsed<Choice>>(
            Parser.Default.parseArguments<Choice>(arrayOf("--file", "in.txt"))
        )
        val fromTool = assertIs<ParserResult.Parsed<Choice>>(
            Parser.Default.parseStructured<Choice>(mapOf("file" to "in.txt"))
        )
        assertEquals(null, fromCli.value.text)
        assertEquals(null, fromTool.value.text)
        assertEquals("in.txt", fromCli.value.file)
        assertEquals("in.txt", fromTool.value.file)
    }

    @Test
    fun `integer schema and parser agree on Int bounds`() {
        data class Options(@Option(longName = "count") var count: Int = 0)
        val field = assertIs<Map<*, *>>(
            assertIs<Map<*, *>>(Parser.Default.describe<Options>().toJsonSchema()["properties"])["count"]
        )
        assertEquals(Int.MIN_VALUE, field["minimum"])
        assertEquals(Int.MAX_VALUE, field["maximum"])
        assertIs<ParserResult.NotParsed<Options>>(
            Parser.Default.parseStructured<Options>(mapOf("count" to Int.MAX_VALUE.toLong() + 1))
        )
    }

    @Test
    fun `nonfinite doubles and numeric short aliases fail explicitly`() {
        data class NumberOption(@Option(longName = "amount") var amount: Double = 0.0)
        assertIs<ParseError.InvalidType>(
            assertIs<ParserResult.NotParsed<NumberOption>>(
                Parser.Default.parseArguments<NumberOption>(arrayOf("--amount", "NaN"))
            ).errors.single()
        )
        data class NumericAlias(@Option(shortName = '1') var value: String = "")
        assertIs<ParseError.ValidationFailed>(
            assertIs<ParserResult.NotParsed<NumericAlias>>(
                Parser.Default.parseArguments<NumericAlias>(arrayOf("-1", "hello"))
            ).errors.single()
        )
    }

    @Test
    fun `custom converter override has an explicit schema and matching structured behavior`() {
        data class Options(@Option(longName = "count") var count: Int = 0)
        val parser = Parser(mapOf(Int::class to object : TypeConverter<Int> {
            override fun convert(value: String): Int = value.length
        }))
        assertFailsWith<IllegalArgumentException> { parser.describe<Options>().toJsonSchema() }
        val schema = parser.describe<Options>().toJsonSchema(mapOf(Int::class to mapOf("type" to "string")))
        assertEquals("string", assertIs<Map<*, *>>(
            assertIs<Map<*, *>>(schema["properties"])["count"]
        )["type"])
        assertEquals(3, assertIs<ParserResult.Parsed<Options>>(
            parser.parseStructured<Options>(mapOf("count" to "abc"))
        ).value.count)
        assertEquals(3, assertIs<ParserResult.Parsed<Options>>(
            parser.parseArguments<Options>(arrayOf("--count", "abc"))
        ).value.count)
    }

    @Test
    fun `invalid and sensitive annotation defaults fail schema validation`() {
        data class InvalidNumber(@Option(longName = "count", default = "oops") var count: Int = 0)
        data class SensitiveDefault(
            @Option(longName = "token", sensitive = true, default = "hardcoded") var token: String = ""
        )
        @OptionGroup("input")
        data class MultipleDefaults(
            @Option(longName = "text", group = "input", default = "hello") var text: String = "",
            @Option(longName = "file", group = "input", default = "in.txt") var file: String = ""
        )
        for (result in listOf(
            Parser.Default.parseArguments<InvalidNumber>(emptyArray()),
            Parser.Default.parseArguments<SensitiveDefault>(emptyArray()),
            Parser.Default.parseArguments<MultipleDefaults>(emptyArray())
        )) {
            assertIs<ParseError.ValidationFailed>(
                assertIs<ParserResult.NotParsed<*>>(result).errors.single()
            )
        }
    }

    @Test
    fun `required grouped default is suppressed by an explicit alternate member`() {
        @OptionGroup("input", required = true)
        data class Choice(
            @Option(longName = "text", required = true, group = "input", default = "fallback")
            var text: String? = null,
            @Option(longName = "file", group = "input") var file: String? = null
        )
        assertIs<ParserResult.Parsed<Choice>>(
            Parser.Default.parseArguments<Choice>(arrayOf("--file", "in.txt"))
        )
        assertIs<ParserResult.Parsed<Choice>>(
            Parser.Default.parseStructured<Choice>(mapOf("file" to "in.txt"))
        )
    }

    @Test
    fun `positional schemas reject gaps and required values after optional ones`() {
        data class Gap(@Value(index = 1) var second: String = "")
        data class RequiredAfterOptional(
            @Value(index = 0) var first: String = "",
            @Value(index = 1, required = true) var second: String = ""
        )
        assertIs<ParseError.ValidationFailed>(
            assertIs<ParserResult.NotParsed<Gap>>(
                Parser.Default.parseArguments<Gap>(arrayOf("one", "two"))
            ).errors.single()
        )
        assertIs<ParseError.ValidationFailed>(
            assertIs<ParserResult.NotParsed<RequiredAfterOptional>>(
                Parser.Default.parseArguments<RequiredAfterOptional>(arrayOf("one", "two"))
            ).errors.single()
        )
    }

    @Test
    fun `argv diagnostics include token positions and sources`() {
        data class Options(
            @Option(longName = "count", default = "3") var count: Int = 0,
            @Option(longName = "verbose") var verbose: Boolean = false
        )
        val parsed = assertIs<ParserResult.Parsed<Options>>(
            Parser.Default.parseArguments<Options>(arrayOf("--verbose"))
        )
        assertEquals(ValueSource.CLI, parsed.sources["verbose"])
        assertEquals(ValueSource.ANNOTATION_DEFAULT, parsed.sources["count"])
        val error = assertIs<ParseError.UnknownOption>(
            assertIs<ParserResult.NotParsed<Options>>(
                Parser.Default.parseArguments<Options>(arrayOf("--verbose", "--unknown"))
            ).errors.single()
        )
        assertEquals(1, error.tokenIndex)
    }
}
