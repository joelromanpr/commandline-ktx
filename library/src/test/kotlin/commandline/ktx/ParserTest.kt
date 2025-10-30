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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTest {

    // --- Basic Parsing Tests ---

    @Test
    fun `parses basic string and boolean options`() {
        data class Options(@Option(longName = "name") var name: String = "", @Option(shortName = 'v') var verbose: Boolean = false)
        val args = arrayOf("--name", "test", "-v")
        val result = Parser.Default.parseArguments<Options>(args)
        val options = assertIs<ParserResult.Parsed<Options>>(result).value
        assertEquals("test", options.name)
        assertTrue(options.verbose)
    }

    @Test
    fun `parses positional value arguments`() {
        data class Options(@Value(index = 0) var input: String = "", @Value(index = 1) var output: String = "")
        val args = arrayOf("input.txt", "output.txt")
        val result = Parser.Default.parseArguments<Options>(args)
        val options = assertIs<ParserResult.Parsed<Options>>(result).value
        assertEquals("input.txt", options.input)
        assertEquals("output.txt", options.output)
    }

    @Test
    fun `parses list of strings with separator`() {
        data class Options(@Option(longName = "tags", separator = ",") var tags: List<String> = emptyList())
        val args = arrayOf("--tags", "a,b,c")
        val result = Parser.Default.parseArguments<Options>(args)
        val options = assertIs<ParserResult.Parsed<Options>>(result).value
        assertEquals(listOf("a", "b", "c"), options.tags)
    }

    // --- Default Value and Precedence Tests ---

    @Test
    fun `uses default value from annotation when not provided`() {
        data class Options(@Option(longName = "name", default = "default-name") var name: String = "")
        val result = Parser.Default.parseArguments<Options>(arrayOf())
        val options = assertIs<ParserResult.Parsed<Options>>(result).value
        assertEquals("default-name", options.name)
    }

    @Test
    fun `command line argument overrides annotation default`() {
        data class Options(@Option(longName = "name", default = "default-name") var name: String = "")
        val args = arrayOf("--name", "cli-name")
        val result = Parser.Default.parseArguments<Options>(args)
        val options = assertIs<ParserResult.Parsed<Options>>(result).value
        assertEquals("cli-name", options.name)
    }

    // --- Validation and Error Tests ---

    @Test
    fun `returns MissingRequired error when required option is missing`() {
        data class Options(@Option(longName = "name", required = true) var name: String = "")
        val result = Parser.Default.parseArguments<Options>(arrayOf())
        val errors = assertIs<ParserResult.NotParsed<Options>>(result).errors
        assertIs<ParseError.MissingRequired>(errors.first())
        assertEquals("name", (errors.first() as ParseError.MissingRequired).option)
    }

    @Test
    fun `returns UnknownOption error for unrecognised options`() {
        data class Options(@Option(longName = "name") var name: String = "")
        val result = Parser.Default.parseArguments<Options>(arrayOf("--invalid"))
        val errors = assertIs<ParserResult.NotParsed<Options>>(result).errors
        assertIs<ParseError.UnknownOption>(errors.first())
    }

    @Test
    fun `returns InvalidType error for wrong value type`() {
        data class Options(@Option(longName = "count") var count: Int = 0)
        val result = Parser.Default.parseArguments<Options>(arrayOf("--count", "abc"))
        val errors = assertIs<ParserResult.NotParsed<Options>>(result).errors
        assertIs<ParseError.InvalidType>(errors.first())
    }

    @Test
    fun `returns MissingValue error when option expects a value but gets none`() {
        data class Options(@Option(longName = "name") var name: String = "")
        val result = Parser.Default.parseArguments<Options>(arrayOf("--name"))
        val errors = assertIs<ParserResult.NotParsed<Options>>(result).errors
        assertIs<ParseError.MissingValue>(errors.first())
    }

    // --- Annotation Feature Tests ---

    @Test
    fun `validates integer within Range`() {
        data class Options(@Option(longName = "count") @Range(min = 1, max = 10) var count: Int = 0)
        val result = Parser.Default.parseArguments<Options>(arrayOf("--count", "5"))
        assertIs<ParserResult.Parsed<Options>>(result)
    }

    @Test
    fun `returns ValidationFailed error for integer outside Range`() {
        data class Options(@Option(longName = "count") @Range(min = 1, max = 10) var count: Int = 0)
        val result = Parser.Default.parseArguments<Options>(arrayOf("--count", "11"))
        val errors = assertIs<ParserResult.NotParsed<Options>>(result).errors
        assertIs<ParseError.ValidationFailed>(errors.first())
    }

    @Test
    fun `parses using custom TypeConverter`() {
        data class Uri(val host: String)
        class UriConverter : TypeConverter<Uri> {
            override fun convert(value: String) = Uri(value)
        }
        data class Options(@Option(longName = "uri") var serviceUri: Uri? = null)

        val parser = Parser(mapOf(Uri::class to UriConverter()))
        val result = parser.parseArguments<Options>(arrayOf("--uri", "example.com"))

        val options = assertIs<ParserResult.Parsed<Options>>(result).value
        assertNotNull(options.serviceUri)
        assertEquals("example.com", options.serviceUri?.host)
    }

    @Test
    fun `enforces mutually exclusive option group`() {
        @OptionGroup("input")
        data class Options(
            @Option(longName = "text") var text: String? = null,
            @Option(longName = "file") var file: String? = null
        )
        val result = Parser.Default.parseArguments<Options>(arrayOf("--text", "a", "--file", "b"))
        val errors = assertIs<ParserResult.NotParsed<Options>>(result).errors
        assertIs<ParseError.ValidationFailed>(errors.first())
    }

    @Test
    fun `enforces required option group`() {
        @OptionGroup("input", required = true)
        data class Options(
            @Option(longName = "text") var text: String? = null,
            @Option(longName = "file") var file: String? = null
        )
        val result = Parser.Default.parseArguments<Options>(arrayOf())
        val errors = assertIs<ParserResult.NotParsed<Options>>(result).errors
        assertIs<ParseError.MissingRequired>(errors.first())
    }
}
