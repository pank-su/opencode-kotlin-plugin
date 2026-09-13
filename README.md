# OpenCode Kotlin

Kotlin/JS-библиотека для in-process плагинов OpenCode 2. Поддерживает два равноправных стиля:

- **DSL** — явная сборка plugin definition;
- **annotations + KSP** — compile-time генерация того же DSL и ESM entrypoint.

Secret Guard в корне репозитория служит рабочим annotation-first примером и реально загружается OpenCode.

## Модули

```text
generator ──generates──▶ core

permissions ──depends──▶ plugin ──depends──▶ core
tools ────────depends──▶ plugin
tui ──────────depends──────────────────────▶ core

processor ──generates Kotlin from annotations; runtime dependency отсутствует
```

| Artifact | Назначение |
|---|---|
| `core` | Generated `external` declarations полного OpenCode SDK |
| `plugin` | Базовый DSL, setup lifecycle и `@OpenCodePlugin` |
| `permissions` | Permission DSL, helpers и `@OpenCodePermission` |
| `tools` | Tool DSL, kotlinx.serialization schema inference и `@OpenCodeTool` |
| `tui` | Отдельный TUI definition, dialog/toast API и TUI annotations |
| `processor` | KSP2 processor для annotation-first API |

`permissions`, `tools` и `tui` не тянут друг друга. Подключаются только нужные capabilities.

## Короткие зависимости

```kotlin
repositories {
    mavenLocal() // либо репозиторий, куда опубликованы artifacts
}

kotlin {
    sourceSets {
        jsMain.dependencies {
            implementation("us.panks.opencode:permissions:0.2.0")
            implementation("us.panks.opencode:tools:0.2.0")
            // implementation("us.panks.opencode:tui:0.2.0")
        }
    }
}
```

Для raw generated API достаточно:

```kotlin
implementation("us.panks.opencode:core:0.2.0")
```

## Вариант 1: annotations

```kotlin
@Serializable
data class InspectInput(val path: String)

@OpenCodePlugin("example.guard")
class GuardPlugin {
    @OpenCodePermission
    suspend fun guardRead(event: PermissionEvaluation) {
        if (event.action == "read" && event.resourcePaths.any { it.endsWith(".env") }) {
            event.deny("Reading environment files is forbidden")
        }
    }

    @OpenCodeTool(
        name = "inspect_path",
        description = "Inspect a path",
        namespace = "guard",
        namespaceDescription = "Guard tools",
    )
    suspend fun inspect(
        input: InspectInput,
        context: ToolContext,
    ): ToolResult = toolResult(content = input.path)
}
```

KSP генерирует:

- `createPluginDefinition()` с `@JsExport`;
- permission registrations;
- tool namespace и definition;
- JSON Schema из `InspectInput.serializer().descriptor`;
- декодирование plain JS input в `InspectInput`;
- Promise bridge для `suspend` handlers.

Настройка consumer-проекта:

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("com.google.devtools.ksp") version "2.3.7"
}

dependencies {
    add("kspJs", "us.panks.opencode:processor:0.2.0")
}
```

Класс plugin должен быть top-level, не `abstract`, иметь конструктор без аргументов. Все annotated handlers обязаны быть `suspend` и возвращать прямой Kotlin-тип (`Unit` или `ToolResult`), а не `Promise`. Они не могут быть `private`/`protected`. Ошибочные сигнатуры, unsupported serializers, duplicate tool names и conflicting namespaces останавливают компиляцию.

## Вариант 2: DSL

Те же runtime-модули можно использовать без processor:

```kotlin
@Serializable
data class InspectInput(val path: String)

val plugin = opencodePlugin("example.guard") {
    permissions {
        evaluate { event ->
            if (event.action == "read") event.deny("Blocked")
        }
    }
    tools {
        transform {
            namespace("guard", "Guard tools")
            tool<InspectInput>("inspect_path") {
                description = "Inspect a path"
                options {
                    namespace = "guard"
                    codeMode = false
                }
                execute { input, _ ->
                    toolResult(content = input.path)
                }
            }
        }
    }
}
```

В DSL `evaluate`, `execute` и `tuiPlugin` также принимают suspend lambdas: ergonomic API всегда пересекает JS-границу как Promise, без параллельных sync/async методов.

`objectSchema { ... }` остаётся только escape hatch для raw external/non-serializable inputs. Для обычной Kotlin-модели schema не дублируется.

Runtime schema inference поддерживает primitives, enums, classes, lists, nullable values, optional/default properties и maps со строковыми ключами. Unknown fields запрещены. Recursive и polymorphic/contextual descriptors отклоняются fail-fast.

Annotation mode дополнительно и заранее отклоняет custom serializers, generic/value/sealed/abstract root models и недоказуемые contextual/polymorphic свойства. Для них используется явный DSL serializer/schema overload.

## TUI как отдельная capability

### TUI annotations

```kotlin
import js.promise.await

@OpenCodeTuiPlugin("example.tui")
class GuardTui {
    @TuiStart
    suspend fun start(context: TuiContext) {
        context.toast(
            message = "Guard loaded",
            variant = ToastVariants.SUCCESS,
        )

        val confirmed = context.confirm(
            title = "Continue?",
            message = "Proceed with the operation?",
        ).await()
    }
}
```

По умолчанию генерируется `createTuiPluginDefinition()`.

### TUI DSL

```kotlin
val tui = tuiPlugin("example.tui") { context ->
    context.toast("Ready", variant = ToastVariants.INFO)
}
```

`tuiPlugin` всегда выполняет suspend setup и возвращает Promise на JS-границе. Для setup с cleanup используется `tuiPluginWithCleanup`; его public cleanup type — `suspend () -> Unit`, автоматически адаптируемый в upstream Promise-returning function.

Модуль предоставляет `toast`, `alert`, `confirm`, `prompt` и generic `select`. `alert` возвращает `Promise<Unit>`, cancellation остальных upstream dialogs сохраняется как nullable result.

## Generator и core

`generator/` — непубликуемый Bun/TypeScript/Karakum toolchain. Он обрабатывает 57 `.d.ts` закреплённого `@opencode-ai/plugin@0.0.0-beta-19271` и пишет только в `core/build/generated/kotlin`.

Проверки фиксируют:

- 340 Kotlin declaration files;
- 208 direct declarations;
- все 49 re-export declarations;
- TypeScript `null | undefined` semantics;
- POSIX-normalized deterministic manifest;
- повторяемый SHA-256 regeneration digest.

`core` объявляет точную npm runtime dependency `@opencode-ai/plugin@0.0.0-beta-19271`, поэтому Gradle/Kotlin JS consumers получают нужный package автоматически.

## Secret Guard example

Example:

- запрещает `read` для `.env`, `.env.*` и стандартных SSH private keys;
- разрешает настоящие `.env.example`, `.env.sample`, `.env.template`;
- проверяет requested и canonical path;
- блокирует path fail-closed при ошибке `realpath`;
- использует один inspector для permission hook и `kotlin_guard_inspect_path`;
- не читает содержимое защищаемых файлов.

Сборка и реальная проверка:

```bash
bun install
bun run check
```

OpenCode config уже подключает `./build/plugin`. Проверка activation:

```bash
bun run verify:opencode
```

## Публикация в Maven Local

```bash
./gradlew \
  :core:publishToMavenLocal \
  :plugin:publishToMavenLocal \
  :permissions:publishToMavenLocal \
  :tools:publishToMavenLocal \
  :tui:publishToMavenLocal \
  :processor:publishToMavenLocal
```

## Ограничения безопасности Secret Guard

Это example защитного слоя, а не sandbox:

- permission hook не анализирует произвольные shell-команды вроде `cat .env`;
- `realpath` закрывает обычные symlink aliases, но между проверкой и чтением остаётся TOCTOU race;
- OpenCode hook передаёт pathname, а не уже открытый file descriptor;
- hardlink нельзя надёжно распознать по одному пути;
- другие plugins и внешние процессы могут читать файлы иными способами.

## Проверенные версии

- Kotlin `2.3.21`;
- Gradle `9.3.1`;
- KSP `2.3.7`;
- kotlinx.serialization `1.9.0`;
- OpenCode CLI/SDK `0.0.0-beta-19271`;
- Karakum `1.0.0-alpha.112`;
- TypeScript `6.0.2`.

## Лицензия

MIT.
