# commandline-ktx

[![Build Status](https://img.shields.io/github/actions/workflow/status/joelromanpr/commandline-ktx/verify.yml?branch=main)](https://github.com/joelromanpr/commandline-ktx/actions/workflows/verify.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.joelromanpr/commandline-ktx.svg)](https://search.maven.org/artifact/io.github.joelromanpr/commandline-ktx)

A small **Kotlin/JVM 17** library for defining command inputs. Annotate mutable properties once, then parse terminal arguments or typed maps from jobs and AI tools. The parser returns values or errors without exiting your application.

## What can you build?

These are possible uses, not claims of existing deployments.

| Area | JVM-hosted example |
| --- | --- |
| Data collection and analytics | Validate a required source and integer batch size before an application imports or processes data. |
| Electronics and manufacturing test | Validate instrument address and test limits before application code talks to a device. |
| Space and scientific operations | Configure ground-side telemetry replays, archive exports, or simulator runs. |
| Mobile teams | Run Android build, test, and release tools on a developer computer or CI server. |
| AI and developer tools | Use one input definition for a CLI and an operation exposed by an existing tool host. |

The library handles inputs and validation. Your application supplies data collection, hardware access, execution, permissions, and output handling.

## Install

Add the published core library to a Java 17 project:

```kotlin
dependencies {
    implementation("io.github.joelromanpr:commandline-ktx:2.0.0")
}
```

Maven:

```xml
<dependency>
    <groupId>io.github.joelromanpr</groupId>
    <artifactId>commandline-ktx</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Your first command

Define mutable properties, parse arguments, and handle the result in your application:

```kotlin
import com.joelromanpr.commandline.ktx.Parser
import com.joelromanpr.commandline.ktx.annotations.Option
import com.joelromanpr.commandline.ktx.core.ParserResult

data class ImportOptions(
    @Option(longName = "source", required = true, helpText = "File to import")
    var source: String = "",
    @Option(longName = "batch-size", default = "100", helpText = "Rows per batch")
    var batchSize: Int = 100
)

fun main(args: Array<String>) {
    val parser = Parser.Default
    if ("--help" in args || "-h" in args) {
        println(parser.generateHelpText<ImportOptions>())
        return
    }
    when (val result = parser.parseArguments<ImportOptions>(args)) {
        is ParserResult.Parsed ->
            println("Source: ${result.value.source}, batch size: ${result.value.batchSize}")
        is ParserResult.NotParsed ->
            result.errors.forEach { System.err.println(it.message) }
    }
}
```

For example, pass `--source readings.csv --batch-size 50`. The example prints the validated inputs; your code performs the import. The built-in input types are `String`, `Int`, `Double`, `Boolean`, and `List<String>`.

## Reuse the inputs for a tool or job

The same `ImportOptions` class can describe and validate a structured call:

```kotlin
val parser = Parser.Default
val inputSchema = parser.describe<ImportOptions>().toJsonSchema()
val result = parser.parseStructured<ImportOptions>(
    mapOf("source" to "readings.csv", "batch-size" to 50)
)
```

`inputSchema` describes the inputs as JSON Schema. `parseStructured` accepts a typed map, rejects unknown or invalid fields, and does not read ambient config files or environment variables. Your host decodes JSON text before calling it.

For an existing MCP host, add the optional bridge and create a tool descriptor from the same class:

```kotlin
dependencies {
    implementation("io.github.joelromanpr:commandline-ktx-mcp-bridge:2.0.0")
}
```

```kotlin
import com.joelromanpr.commandline.ktx.mcp.mcpTool

val tool = Parser.Default.mcpTool<ImportOptions>("import_data", "Import a data file")
val descriptor = tool.descriptor()
```

The bridge validates calls before your handler runs. It does not include an MCP server, transport, or authorization. See the [bridge example](mcp-bridge/README.md) for invocation and errors.

## Learn more

- [Project map](docs/PROJECT_MAP.md): supported inputs, config and environment precedence, converters, groups, schemas, and current limits.
- [Version 2.0.0 release notes](docs/releases/v2.0.0.md): changes and the required `@OptionGroup` migration from 1.0.0.
- [Contributing](CONTRIBUTING.md): local checks, pull requests, and the maintainer release path.

### Migrating option groups from 1.0.0

In 2.0.0, each intended member of an `@OptionGroup` must name that group, such as `@Option(longName = "text", group = "input")` or `@Value(index = 0, group = "input")`. Leave unrelated inputs ungrouped.

This release targets Java 17 on the JVM. On-device Android compatibility has not been verified; iOS/native, device firmware, and flight software are outside the published targets.

## License

MIT. See [LICENSE](LICENSE).
