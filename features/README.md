# OpenCode Kotlin Features

Handwritten ergonomic API поверх `opencode-kotlin-core`. Модуль скрывает JS object construction, generated `Info`/`ToolEditor`, registration sequencing и cleanup lifecycle.

## Maven coordinates

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("us.panks.opencode:opencode-kotlin-features:0.1.0")
}
```

## Пример DSL

```kotlin
private external interface InspectInput {
    val path: String
}

val plugin = opencodePlugin("example.guard") {
    permissions {
        evaluate { event ->
            if (event.action == "read" && event.resourcePaths.any { it.endsWith(".env") }) {
                event.deny("Reading environment files is forbidden")
            }
        }
    }
    tools {
        transform {
            namespace("guard", "Guard tools")
            tool<InspectInput, ToolResult>("inspect_path") {
                description = "Inspect a path"
                input(objectSchema {
                    string("path", required = true)
                    additionalProperties = false
                })
                options {
                    namespace = "guard"
                    codeMode = false
                }
                execute { input, _ ->
                    toolResult(content = "PATH: ${input.path}")
                }
            }
        }
    }
}
```

Для асинхронного инструмента используется `executeAsync`. `setup` выполняет registrations последовательно и возвращает cleanup, освобождающий их в обратном порядке.

## Команды

```bash
./gradlew :features:jsNodeTest
./gradlew :features:publishToMavenLocal
```
