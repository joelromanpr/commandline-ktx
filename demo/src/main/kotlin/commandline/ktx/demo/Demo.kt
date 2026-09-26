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
package com.joelromanpr.commandline.ktx.demo

import com.joelromanpr.commandline.ktx.Parser
import com.joelromanpr.commandline.ktx.annotations.Application
import com.joelromanpr.commandline.ktx.annotations.ConfigFile
import com.joelromanpr.commandline.ktx.annotations.EnvVar
import com.joelromanpr.commandline.ktx.annotations.Option
import com.joelromanpr.commandline.ktx.annotations.OptionGroup
import com.joelromanpr.commandline.ktx.annotations.Range
import com.joelromanpr.commandline.ktx.annotations.Value
import com.joelromanpr.commandline.ktx.core.ParserResult
import java.io.File
import java.net.URL

@Application(
    name = "Enhanced Demo",
    version = "2.0.0",
    description = "A demo showcasing the power of commandline-ktx."
)
@ConfigFile("config.properties")
@OptionGroup(name = "input-source", required = true, helpText = "One of --text, --uri, or an input file must be provided.")
data class Options(
    @Option(
        shortName = 'v',
        longName = "verbose",
        helpText = "Enable verbose output to see detailed logs."
    )
    var verbose: Boolean = false,

    @Option(
        shortName = 'n',
        longName = "name",
        helpText = "The name to use in the greeting."
    )
    @EnvVar("DEMO_NAME")
    var name: String = "Guest",

    @Option(
        shortName = 'c',
        longName = "count",
        helpText = "The number of times to repeat the greeting.",
        default = "5" // Demonstrate default value from annotation
    )
    @Range(min = 1, max = 100)
    var count: Int = 1,

    @Option(
        longName = "uri",
        helpText = "A URL to fetch the greeting text from.",
        group = "input-source"
    )
    var uri: Uri? = null,

    @Option(
        longName = "text",
        helpText = "A custom greeting text to display.",
        group = "input-source"
    )
    var text: String? = null,

    @Value(
        index = 0,
        helpText = "An input file to read the greeting from.",
        group = "input-source"
    )
    var inputFile: String? = null,

    @Value(
        index = 1,
        required = false,
        helpText = "The output file to write the result to."
    )
    var outputFile: String = ""
)

fun main(args: Array<String>) {
    println("=".repeat(50))
    println("🚀 Welcome to the CommandLine-KTX Demo 🚀")
    println("=".repeat(50) + "\n")

    val parser = Parser(mapOf(Uri::class to UriConverter()))

    if (args.contains("--help") || args.contains("-h")) {
        println(parser.generateHelpText<Options>())
        return
    }

    when (val result = parser.parseArguments<Options>(args)) {
        is ParserResult.Parsed -> {
            val options = result.value
            println("✅ Arguments parsed successfully!")
            println("━".repeat(50))
            println("Configuration:")
            println("  • Verbose: ${if (options.verbose) "Enabled" else "Disabled"}")
            println("  • Name: ${options.name}")
            println("  • Repetitions: ${options.count}")
            options.uri?.let { println("  • URI: ${it.scheme}://${it.host}:${it.port}") }
            options.text?.let { println("  • Text: $it") }
            options.inputFile?.let { println("  • Input File: $it") }
            println("  • Output File: ${options.outputFile.ifEmpty { "[Printing to Console]" }}")
            println("━".repeat(50))

            // Determine the greeting message based on a clear precedence
            val greeting = options.text ?: run {
                try {
                    options.uri?.let {
                        println("ℹ️ Fetching greeting from ${it.scheme}://${it.host}:${it.port}...")
                        URL("${it.scheme}://${it.host}:${it.port}").readText().trim()
                    } ?: options.inputFile?.let {
                        File(it).readText().trim()
                    }
                } catch (e: Exception) {
                    println("⚠️  Could not read from input source: ${e.message}")
                    null
                }
            }

            val content = buildString {
                if (options.verbose) {
                    appendLine("🔊 Verbose Mode: Detailed log enabled.")
                    repeat(options.count) { i ->
                        appendLine("  [${i + 1}/${options.count}] ${greeting ?: "Hello"}, ${options.name}!")
                    }
                } else {
                    appendLine("👋 ${greeting ?: "Hello"}, ${options.name}!")
                    if (options.count > 1) {
                        appendLine("   (Action will be performed ${options.count} times)")
                    }
                }
            }

            if (options.outputFile.isNotEmpty()) {
                try {
                    val outputFile = File(options.outputFile)
                    // Ensure parent directories exist before writing the file
                    outputFile.parentFile?.mkdirs()
                    outputFile.writeText(content)
                    println("✅ Successfully wrote output to '${options.outputFile}'")
                } catch (e: Exception) {
                    println("❌ Failed to write to output file '${options.outputFile}': ${e.message}")
                }
            } else {
                println(content)
            }
            println("\n" + "=".repeat(50))
            println("✨ Demo Finished Successfully ✨")
            println("=".repeat(50))
        }
        is ParserResult.NotParsed -> {
            println("❌ Failed to parse arguments:\n")
            result.errors.forEach { error ->
                println("  • ${error.message}")
            }
            println("\n" + "━".repeat(50))
            println(parser.generateHelpText<Options>())
            println("\n" + "=".repeat(50))
            println("Demo Finished with Errors")
            println("=".repeat(50))
        }
    }
}
