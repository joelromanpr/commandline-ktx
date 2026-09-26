# MCP bridge

`commandline-ktx-mcp-bridge` turns one annotated command into JSON-compatible MCP tool descriptor fields and validates tool arguments through the same parser used for command-line input. It adds no MCP server, transport, or SDK dependency.

An existing JVM-based MCP host could use it to expose a data import, laboratory test, or ground-side telemetry query. These are example integrations; this module supplies input metadata and validation, while the host supplies the operation and its permissions.

Integration sketch (`countItems`, `sendResult`, and `sendToolError` are application functions):

```kotlin
class CountInput {
    @Option(longName = "count", required = true)
    var count: Int = 0
}

val tool = Parser.Default.mcpTool<CountInput>(
    name = "count_items",
    description = "Count the requested items"
)
val descriptor = tool.descriptor() // name, description, inputSchema

val result = tool.invoke(mapOf("count" to 3)) { input ->
    countItems(input.count)
}
when (result) {
    is McpInvocationResult.Success -> sendResult(result.value)
    is McpInvocationResult.InvalidArguments -> sendToolError(result.asMcpToolResult())
}
```

Your MCP host serializes the descriptor and result fields using its own SDK. It also owns authorization, rate limits, confirmation for side effects, and output serialization. Invalid arguments return stable error codes and field names without echoing supplied values. A custom converter requires a matching JSON Schema through `mcpTool(customTypeSchemas = ...)`.
