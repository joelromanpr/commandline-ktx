# Agent guide

## Purpose and layout

`commandline-ktx` is a Kotlin/JVM 17 library for mapping command-line arguments to mutable Kotlin properties through runtime annotations and reflection. It returns `ParserResult` values instead of exiting the process. The published artifacts are `io.github.joelromanpr:commandline-ktx` and the optional `io.github.joelromanpr:commandline-ktx-mcp-bridge`.

- `library/src/main/kotlin/com/joelromanpr/commandline/ktx/`: public parser, annotations, converter interface, and result/error types. `Parser.kt` owns token scanning, defaults, validation, and help text.
- `library/src/test/kotlin/commandline/ktx/ParserTest.kt`: parser behavior tests.
- `mcp-bridge/`: optional published MCP descriptor and validation bridge; it has no MCP SDK or server runtime dependency.
- `demo/`: sample application; it is not published.
- `docs/PROJECT_MAP.md`: architecture, verified limitations, and candidate improvements. `CommandSpec.kt` is the validated command description for structured input and JSON Schema export.
- `CONTRIBUTING.md`: the branch, pull request, and release path for everyone.

## Working in this repository

- Start from current `main` on a descriptive topic branch. Use a fork for an external contribution or a branch in this repository if you have write access. Open a pull request to `main`; do not push changes directly to `main`.
- Preserve pre-existing worktree changes and keep generated `build/`, `.gradle/`, and local credentials out of commits.
- Keep library changes compatible with the public API unless a breaking change is explicitly intended and explained in the pull request. The library uses Kotlin's `explicitApi()` mode; public declarations need explicit visibility and return types.
- Put behavior changes in `library`, with focused tests in the library test suite. Use `demo` to show a real consumer path, but do not rely on its console output as the only test.
- Do not run Maven Central publish tasks as part of ordinary validation. Releases use the separate tag-triggered workflow after a version change goes through a pull request.

## Style and checks

- Kotlin code uses four-space indentation, the existing MIT source header, and `kotlin.code.style=official`. Follow neighboring naming and KDoc conventions.
- Root Gradle configuration pins Kotlin 2.2.20 and Spotless 8.0.0; the wrapper is Gradle 8.14. Java 17 is the configured toolchain.
- Run `./gradlew spotlessCheck build` before a pull request. `./scripts/prepare_for_pr.sh --check -y` is a check-only Spotless shortcut. The script's default mode applies formatting and changes files.
- If behavior changes, test the observable `ParserResult` or generated help text, including invalid input and precedence. Avoid tests that only restate the implementation.

## Known boundaries

- `@OptionGroup` declares a group on the class; each member names it through `@Option(group = ...)` or `@Value(group = ...)`. Only one group annotation is supported per class.
- CLI value priority is argv, config file, environment, annotation default, then the property's initial value. `parseStructured` uses explicit values and annotation defaults without reading ambient config or environment.
- Keep CLI parsing, `parseStructured`, `CommandSpec.toJsonSchema()`, and MCP bridge validation aligned. Custom converters accept strings; their JSON Schema must declare `type: string`.
- Config files are resolved against the process working directory. Do not assume module-relative paths.
- Both published modules share one version; tag `vX.Y.Z` on merged `main` triggers the release workflow.
