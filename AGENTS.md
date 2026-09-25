# Agent guide

## Purpose and layout

`commandline-ktx` is a Kotlin/JVM 17 library for mapping command-line arguments to mutable Kotlin properties through runtime annotations and reflection. It returns `ParserResult` values instead of exiting the process. The published artifact is `io.github.joelromanpr:commandline-ktx`.

- `library/src/main/kotlin/com/joelromanpr/commandline/ktx/`: public parser, annotations, converter interface, and result/error types. `Parser.kt` owns token scanning, defaults, validation, and help text.
- `library/src/test/kotlin/commandline/ktx/ParserTest.kt`: parser behavior tests.
- `demo/`: sample application; it is not the published library.
- `docs/PROJECT_MAP.md`: architecture, verified limitations, and candidate improvements.
- `CONTRIBUTING.md`: the branch, pull request, and release path for everyone.

## Working in this repository

- Start from current `main` on a descriptive topic branch. Use a fork for an external contribution or a branch in this repository if you have write access. Open a pull request to `main`; do not push changes directly to `main`.
- Preserve pre-existing worktree changes and keep generated `build/`, `.gradle/`, and local credentials out of commits.
- Keep library changes compatible with the public API unless a breaking change is explicitly intended and explained in the pull request. The library uses Kotlin's `explicitApi()` mode; public declarations need explicit visibility and return types.
- Put behavior changes in `library`, with focused tests in `ParserTest.kt`. Use `demo` to show a real consumer path, but do not rely on its console output as the only test.
- Do not run Maven Central publish tasks as part of ordinary validation. Releases use the separate tag-triggered workflow after a version change goes through a pull request.

## Style and checks

- Kotlin code uses four-space indentation, the existing MIT source header, and `kotlin.code.style=official`. Follow neighboring naming and KDoc conventions.
- Root Gradle configuration pins Kotlin 2.2.20 and Spotless 8.0.0; the wrapper is Gradle 8.14. Java 17 is the configured toolchain.
- Run `./gradlew spotlessCheck build` before a pull request. `./scripts/prepare_for_pr.sh --check -y` is a check-only Spotless shortcut. The script's default mode applies formatting and changes files.
- If behavior changes, test the observable `ParserResult` or generated help text, including invalid input and precedence. Avoid tests that only restate the implementation.

## Known boundaries

- `@OptionGroup` currently has inconsistent behavior: the annotation targets classes, while parsing groups every named option and excludes positional values. Treat this as a bug to fix with focused tests, not as a contract to preserve.
- Current value priority is command line, config file, environment, annotation default, then the property's initial value. Older documentation differed; verify and test the intended order before changing it.
- The scanner currently treats a leading `-` as an option and has no `--` delimiter. Extra positional values and duplicate aliases are not validated. Keep these limitations visible when extending parsing.
- Config files are resolved against the process working directory. Do not assume module-relative paths.
