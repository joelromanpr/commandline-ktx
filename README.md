# commandline-ktx

[![Build Status](https://img.shields.io/github/actions/workflow/status/joelromanpr/commandline-ktx/build_test_publish.yml?branch=main)](https://github.com/joelromanpr/commandline-ktx/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.joelromanpr/commandline-ktx.svg)](https://search.maven.org/artifact/io.github.joelromanpr/commandline-ktx)

A simple, modern, and type-safe command-line argument parser for Kotlin, built with annotations and reflection.

`commandline-ktx` is designed to be intuitive and require minimal boilerplate. Define your arguments in a data class, and let the parser handle the rest.

## Features

- **Declarative:** Define arguments using simple annotations (`@Option`, `@Value`).
- **Type-Safe:** Supports `String`, `Int`, `Double`, `Boolean`, and `List<String>` out of the box.
- **Extensible:** Register custom `TypeConverter` instances for your own data types.
- **Flexible Defaults:** Provide default values from annotations, environment variables (`@EnvVar`), or a configuration file (`@ConfigFile`).
- **Validation:** Enforce required options and create mutually exclusive option groups (`@OptionGroup`).
- **Automatic Help Text:** Generate well-formatted help text from your annotated class.

## Getting Started

`commandline-ktx` is hosted on Maven Central.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.joelromanpr:commandline-ktx:1.0.0")
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.joelromanpr</groupId>
    <artifactId>commandline-ktx</artifactId>
    <version>1.0.0</version>
</dependency>
```

## How to Use

### 1. Define Your Arguments

Create a `data class` and use annotations to define the command-line arguments your application accepts.

- `@Option`: For named options (e.g., `--verbose`, `-f file.txt`).
- `@Value`: For positional arguments (e.g., `input.txt`).

```kotlin
import com.joelromanpr.commandline.ktx.*

@Application(
    name = "MyApp",
    version = "1.0.0",
    description = "A powerful demonstration application."
)
@ConfigFile("config.properties") // Load defaults from a file
@OptionGroup(name = "mode", required = true) // Define a required, mutually exclusive group
data class AppOptions(
    @Option(longName = "text", helpText = "Use a raw text string as input.")
    @OptionGroup("mode")
    var text: String? = null,

    @Value(index = 0, helpText = "Use a file as input.")
    @OptionGroup("mode")
    var inputFile: String? = null,

    @Option(shortName = 'n', longName = "name", helpText = "The name to use in the greeting.")
    @EnvVar("APP_USER_NAME") // Fallback to an environment variable
    var name: String = "Guest",

    @Option(longName = "uri", helpText = "A custom URI to connect to.")
    var serviceUri: Uri? = null // Custom types are supported!
)
```

### 2. Create a Custom Type Converter (Optional)

If you have custom data types, like the `Uri` class above, create a `TypeConverter`.

```kotlin
// Your custom data type
data class Uri(val scheme: String, val host: String, val port: Int)

// The converter
class UriConverter : TypeConverter<Uri> {
    override fun convert(value: String): Uri {
        val parts = value.split("://")
        val scheme = parts[0]
        val rest = parts[1].split(":")
        return Uri(scheme, rest[0], rest.getOrNull(1)?.toInt() ?: 80)
    }
}
```

### 3. Parse the Arguments

In your `main` function, create a `Parser` instance (registering any custom converters) and call `parseArguments`.

```kotlin
fun main(args: Array<String>) {
    // Create a parser, registering the custom converter
    val parser = Parser(mapOf(Uri::class to UriConverter()))

    // Handle the --help flag
    if (args.contains("--help") || args.contains("-h")) {
        println(parser.generateHelpText<AppOptions>())
        return
    }

    when (val result = parser.parseArguments<AppOptions>(args)) {
        is ParserResult.Parsed -> {
            val options = result.value
            println("Success! Welcome, ${options.name}.")
            // ... your application logic ...
        }
        is ParserResult.NotParsed -> {
            println("Error parsing arguments:")
            result.errors.forEach { println("  - ${it.message}") }
            println("\n" + parser.generateHelpText<AppOptions>())
        }
    }
}
```

### 4. Provide a Configuration File (Optional)

Create the `config.properties` file specified in the `@ConfigFile` annotation. Values from this file are used if they are not provided on the command line or via environment variables.

```properties
# config.properties

# The default name to use
name = ConfigDefault
```

## Automatic Help Text

Calling `parser.generateHelpText<AppOptions>()` produces a clean, formatted help message based on your annotations.

```
MyApp 1.0.0
A powerful demonstration application.

Usage: myapp [options] [arg0]

Configuration file: config.properties

Options:
  --text               Use a raw text string as input. 
  -n, --name           The name to use in the greeting. (env: APP_USER_NAME)
  --uri                A custom URI to connect to. 

Option Groups:
  mode (required)

Arguments:
  <arg0> Use a file as input.
```

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for the full license text.
