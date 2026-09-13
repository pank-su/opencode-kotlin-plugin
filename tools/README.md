# tools

Опциональная capability для OpenCode tools.

```kotlin
implementation("us.panks.opencode:tools:0.2.0")
```

`@Serializable` input автоматически даёт JSON Schema и runtime decoding.

## DSL

```kotlin
@Serializable
data class Input(val path: String)

val plugin = opencodePlugin("example.tools") {
    tools {
        transform {
            tool<Input>("inspect") {
                description = "Inspect a path"
                execute { input, _ ->
                    toolResult(content = input.path)
                }
            }
        }
    }
}
```

## Annotations

```kotlin
@OpenCodePlugin("example.tools")
class ToolsPlugin {
    @OpenCodeTool(name = "inspect", description = "Inspect a path")
    suspend fun inspect(input: Input): ToolResult =
        toolResult(content = input.path)
}
```

DSL `execute` и annotated handler всегда suspend; JavaScript получает Promise. Raw `objectSchema` сохранён только как escape hatch.
