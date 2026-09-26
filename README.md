# commandline-ktx

[![Build Status](https://img.shields.io/github/actions/workflow/status/joelromanpr/commandline-ktx/verify.yml?branch=main)](https://github.com/joelromanpr/commandline-ktx/actions/workflows/verify.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.joelromanpr/commandline-ktx.svg)](https://search.maven.org/artifact/io.github.joelromanpr/commandline-ktx)

A small Kotlin/JVM command-line parser. Annotate mutable properties, parse arguments without exiting the process, and handle a `ParserResult` in your application.

Version **2.0.0** adds a deterministic command description, structured input, more precise diagnostics, and an optional MCP bridge. It is a major release because option-group membership now requires explicit property annotations; see the migration section below. The [contribution and release guide](CONTRIBUTING.md) explains how changes reach `main` and how maintainers publish tagged releases.

## Install 2.0.0

Add the core parser and, if needed, the optional MCP bridge:

```kotlin
dependencies {
    implementation("io.github.joelromanpr:commandline-ktx:2.0.0")
    // Optional when exposing a command as an MCP tool:
    implementation("io.github.joelromanpr:commandline-ktx-mcp-bridge:2.0.0")
}
```

For Maven:

```xml
<dependency>
    <groupId>io.github.joelromanpr</groupId>
    <artifactId>commandline-ktx</artifactId>
    <version>2.0.0</version>
</dependency>
<!-- Add the optional bridge only if your application needs it. -->
<dependency>
    <groupId>io.github.joelromanpr</groupId>
    <artifactId>commandline-ktx-mcp-bridge</artifactId>
    <version>2.0.0</version>
</dependency>
```

Both published artifacts use the same version; the `demo` module is an application and is not published.

## Parse a command

Define a class with mutable annotated properties. `@Option` accepts a named value, and `@Value` accepts a positional value. The parser supports `String`, `Int`, `Double`, `Boolean`, and `List<String>`; register a `TypeConverter` for another type.

```kotlin
import com.joelromanpr.commandline.ktx.Parser
import com.joelromanpr.commandline.ktx.annotations.Option
import com.joelromanpr.commandline.ktx.annotations.Value
import com.joelromanpr.commandline.ktx.core.ParserResult

data class Options(
    @Option(longName = "count", default = "1", helpText = "Number of greetings")
    var count: Int = 1,
    @Value(index = 0, required = true, helpText = "Who to greet")
    var name: String = ""
)

fun main(args: Array<String>) {
    val parser = Parser.Default
    if (args.contains("--help") || args.contains("-h")) {
        println(parser.generateHelpText<Options>())
        return
    }
    when (val result = parser.parseArguments<Options>(args)) {
        is ParserResult.Parsed -> println("Hello, ${result.value.name}!".repeat(result.value.count))
        is ParserResult.NotParsed -> result.errors.forEach { System.err.println(it.message) }
    }
}
```

With `@ConfigFile` and `@EnvVar`, command-line values take priority over configuration file values, then environment variables, annotation defaults, and property initial values. A configuration file path is relative to the process working directory. See the [project map](docs/PROJECT_MAP.md) for parser behavior and remaining limitations.

## One command definition for CLI and structured callers

Version 2.0.0 makes group membership explicit on each property. A class-level `@OptionGroup` declares a mutually exclusive group; `group = "input"` puts a named or positional property in it. Other options stay outside the group.

```kotlin
import com.joelromanpr.commandline.ktx.Parser
import com.joelromanpr.commandline.ktx.annotations.Option
import com.joelromanpr.commandline.ktx.annotations.OptionGroup
import com.joelromanpr.commandline.ktx.annotations.Value
import com.joelromanpr.commandline.ktx.core.ParserResult

@OptionGroup(name = "input", required = true)
data class InputOptions(
    @Option(longName = "text", group = "input") var text: String? = null,
    @Value(index = 0, group = "input") var file: String? = null,
    @Option(longName = "verbose") var verbose: Boolean = false
)

val parser = Parser.Default
val spec = parser.describe<InputOptions>()
val schema: Map<String, Any?> = spec.toJsonSchema()
val result = parser.parseStructured<InputOptions>(
    mapOf("text" to "hello", "verbose" to false)
)
if (result is ParserResult.Parsed) println(result.sources) // text and verbose: STRUCTURED
```

`describe<T>()` validates the command definition and returns options in canonical-name order and positional values in index order. `toJsonSchema()` exports an object-root JSON Schema with built-in types, integer bounds, required fields, and group rules. The schema is a description for structured callers; the parser does not parse JSON text or run a general JSON Schema validator. Annotation defaults are available in `CommandSpec`, but are not emitted as JSON Schema `default` values.

`parseStructured(Map<String, Any?>)` accepts typed values under long option names (or the short name when no long name exists) and positional **property names**. It rejects unknown fields and invalid types. It uses annotation defaults and property initial values, but does not read ambient config files or environment variables. For successful calls, `ParserResult.Parsed.sources` maps Kotlin property names to `ValueSource.CLI`, `CONFIG`, `ENVIRONMENT`, `ANNOTATION_DEFAULT`, or `STRUCTURED`; unchanged property initial values have no entry. Failures expose a stable `ParseError.code`, `field`, and an optional zero-based CLI `tokenIndex`.

Custom converters still accept strings. To export a custom type, provide a matching schema explicitly:

```kotlin
import com.joelromanpr.commandline.ktx.Parser
import com.joelromanpr.commandline.ktx.annotations.Option
import com.joelromanpr.commandline.ktx.converters.TypeConverter
import java.net.URI

data class ConnectionOptions(@Option(longName = "uri") var uri: URI? = null)

val parser = Parser(mapOf(URI::class to object : TypeConverter<URI> {
    override fun convert(value: String): URI = URI(value).also { require(it.isAbsolute) }
}))
val schema = parser.describe<ConnectionOptions>().toJsonSchema(
    mapOf(URI::class to mapOf("type" to "string", "format" to "uri"))
)
```

The converter and schema are the application's responsibility: the library cannot infer a custom input shape or enforce arbitrary custom JSON Schema keywords. Set `sensitive = true` on an `@Option` to redact rejected values from diagnostics. Sensitive annotation defaults are rejected; supply secrets through the caller, environment, or config and keep them out of schema descriptions.

### Optional MCP bridge

The `mcp-bridge` module maps a command definition to MCP tool descriptor fields and routes a structured argument map through the parser before invoking a handler:

```kotlin
import com.joelromanpr.commandline.ktx.mcp.mcpTool

val tool = Parser.Default.mcpTool<InputOptions>("greet", "Greet a person")
val descriptor = tool.descriptor() // name, description, inputSchema
```

It has no MCP server, transport, or SDK dependency. The host application owns authorization, confirmation for side effects, and output serialization. See the [bridge example](mcp-bridge/README.md) for invocation and error handling.

### Migrating option groups from 1.0.0

Earlier versions implicitly included every named option in every class-level `@OptionGroup`, while positional values were excluded. In 2.0.0, add `group = "group-name"` to **each intended member**, including positional members. Leave unrelated flags ungrouped. A group without members is an invalid command definition, so update affected classes before upgrading.

## Contributing

Use a short-lived topic branch or fork and open a pull request to `main`. Run `./gradlew spotlessCheck build` before submitting. Merging a pull request does not publish; maintainers release from a versioned tag after verification. See [CONTRIBUTING.md](CONTRIBUTING.md) for the complete path and [docs/PROJECT_MAP.md](docs/PROJECT_MAP.md) for architecture and current opportunities.

## License

MIT. See [LICENSE](LICENSE).
