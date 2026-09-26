# Project map and improvement opportunities

## What this project does

`commandline-ktx` maps command-line arguments to mutable Kotlin properties annotated with `@Option` and `@Value`. It returns `ParserResult.Parsed` or `ParserResult.NotParsed`; the caller chooses how to display help and errors. The parser can use config files, environment variables, annotation defaults, and custom `TypeConverter` instances.

Version 2.0.0 also compiles annotations into a deterministic `CommandSpec`, exports an object-root JSON Schema, and validates a typed `Map<String, Any?>` through `parseStructured`. The optional `mcp-bridge` module turns that command definition into MCP tool descriptor fields and validates arguments before a host-provided handler runs.

## Layout and runtime

| Module | Purpose | Publication |
| --- | --- | --- |
| `library` | Parser, annotations, converter interface, command specification, and result/error types | `io.github.joelromanpr:commandline-ktx` |
| `mcp-bridge` | Thin tool descriptor and invocation adapter, without an MCP server or SDK | `io.github.joelromanpr:commandline-ktx-mcp-bridge` |
| `demo` | Runnable application showing a real parser consumer | Not published |

The public core API lives under `library/src/main/kotlin/com/joelromanpr/commandline/ktx/`. Tests are in `library/src/test/kotlin/commandline/ktx/`; the bridge has its own tests. Java 17 is the target toolchain, and published modules use Kotlin explicit API mode. Reflection and parsing are synchronous.

## Command behavior

1. `Parser.describe<T>()` inspects and validates the annotated class. Named options are ordered by canonical name; positional values are ordered by index. Duplicate aliases/indexes, unknown group references, and invalid annotation defaults fail schema validation.
2. `parseArguments` creates a no-argument instance, scans argv, converts values, applies fallbacks, checks required inputs and mutually exclusive groups, and returns a result. Named and positional group members must explicitly declare `group = "name"`; a class-level `@OptionGroup` declares the group and whether one member is required.
3. CLI values take priority over config file values, environment variables, annotation defaults, and property initial values. Config file paths resolve against the process working directory. `--` ends option scanning; an option can use `--name=value`; negative numbers are accepted for numeric inputs. Extra positional tokens are errors.
4. `parseStructured` accepts typed values keyed by canonical long names (or short names where no long name exists) and positional property names. It rejects unknown fields and invalid types. It applies annotation defaults and property initial values, but does not read config or environment state.
5. A successful `ParserResult.Parsed.sources` records the source of each supplied property. Initial property values do not appear in this map. Errors expose stable codes and field names; CLI token errors can include a zero-based index. A sensitive option redacts rejected values from diagnostics.

`CommandSpec.toJsonSchema()` describes built-in types, integer bounds, required fields, and group rules with `additionalProperties: false`. Application defaults remain in `CommandSpec` metadata but are not exported as JSON Schema `default` values. A custom converter needs a caller-provided schema; the structured parser still passes its string value to that converter and does not validate arbitrary custom schema keywords.

## Remaining limitations and useful contributions

These are candidate issues, not promises. Add a behavior test for a parser change and update public docs when behavior changes.

| Area | Current boundary | Useful next step |
| --- | --- | --- |
| Command shapes | Inputs are mutable properties on a class with a no-argument constructor. There are no nested subcommands or constructor-bound immutable models. | Add one capability at a time with a clear compatibility path. |
| Custom types | Converters accept strings, and their JSON Schema must be supplied separately. The schema and converter can disagree. | Consider an optional schema-aware converter interface and focused agreement tests. |
| Structured results | The library describes inputs, not handler outputs; the bridge has no MCP server, transport, authentication, or output serialization. | Keep these in applications or a separate optional adapter where a concrete use case justifies them. |
| Configuration | Config parsing is a small `key=value` reader using a path relative to the process working directory. | Specify and test escaping, malformed lines, and repeated keys before expanding the format. |
| Help and discovery | Help text covers annotated inputs, but there is no shell completion or command-tree discovery. | Consider after the basic command contract is stable and requested by users. |

## Contribution and release path

Everyone starts from current `main`, works on a short-lived topic branch or fork, and opens a pull request to `main`. CI runs `verify`; contributors can run `./gradlew spotlessCheck build` locally. Merging a pull request does not publish. A maintainer updates both published module versions in a release pull request, merges after verification, then tags the merged commit to start publication. See [CONTRIBUTING.md](../CONTRIBUTING.md) for commands and release policy.
