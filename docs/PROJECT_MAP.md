# Project map and improvement opportunities

## What this project does

`commandline-ktx` provides a small annotation-based argument parser for Kotlin command-line applications. A consumer defines mutable properties annotated with `@Option` or `@Value`, optionally supplies `@EnvVar`, `@ConfigFile`, `@Range`, and a custom `TypeConverter`, then calls `Parser.parseArguments`. The library returns a `ParserResult.Parsed` value or structured errors. `Parser.generateHelpText` creates usage text from the same annotations.

The `library` Gradle module holds the published API. The `demo` module exercises it as an application. Java 17 is the target toolchain, and `library` uses Kotlin explicit API mode. Reflection is a runtime dependency; parsing is synchronous and reads a config file from the current working directory when one is declared.

## How parsing is organized

1. `Parser` creates a no-argument instance of the requested class and inspects its mutable properties.
2. It maps named options and positional indexes from annotations.
3. It scans command-line tokens and converts values with a registered converter or built-in conversion.
4. It fills absent named options from config, environment, or annotation defaults, then checks required values and option groups.
5. It returns a result object; the caller decides how to display errors or help.

The public API is in `library/src/main/kotlin/com/joelromanpr/commandline/ktx/`. The main behavior tests are in `library/src/test/kotlin/commandline/ktx/ParserTest.kt`.

## Confirmed gaps and useful next changes

These are starting points for issues and pull requests, not promises about future behavior. Add a failing behavior test before changing parsing semantics.

| Priority | Current gap | Useful next change |
| --- | --- | --- |
| High | `@OptionGroup` targets classes, so property membership cannot be expressed. The parser places every named option in each class group and excludes positional values. The demo's required input-source group can therefore reject ordinary flags and accept no input source. | Define property membership semantics, test named and positional members plus unrelated flags, then align the annotation, parser, demo, and README. |
| High | There is no schema validation for duplicate option aliases or positional indexes; later properties can silently replace earlier mappings. | Validate a schema before scanning and add duplicate alias/index tests. |
| Medium | A named option with a missing or invalid value can still be marked as supplied, creating misleading secondary required/group diagnostics alongside the parse error. | Mark an option supplied only after successful conversion and test error lists. |
| Medium | Tokens beginning with `-` are always treated as options; `--` and negative numeric values are unsupported. Extra positional values are silently ignored. | Specify token rules, then test delimiter, negative numbers, unknown options, and surplus positionals. |
| Medium | Fallback priority is config before environment, which may surprise applications expecting environment overrides. Boolean values from config, environment, or annotation defaults use `String.toBoolean()`, which accepts malformed text as `false`. | Decide whether this priority should remain; test CLI/config/env/default/initializer combinations and strict Boolean conversion. |
| Medium | Tests cover common options but not config/environment precedence, generated help, malformed schemas, or the demo's input-source path. | Add focused unit tests and a small demo smoke test for public examples. |
| Low | `gradle.properties` still contains `android-essentials` POM URL values, even though the library publication block sets this project's POM URLs. | Remove obsolete properties and keep publication metadata in one place. |

## Contribution shape

Small, isolated changes are easiest to review: one behavior or documentation issue per topic branch, a test showing the intended public outcome, and corresponding README or KDoc updates. See `CONTRIBUTING.md` for the shared fork/branch/PR path and release policy.
