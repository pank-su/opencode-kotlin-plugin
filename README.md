# OpenCode Kotlin

Kotlin/JS-библиотека для разработки in-process плагинов OpenCode 2 и демонстрационный Secret Guard plugin. Репозиторий разделяет generated ABI, handwritten features DSL, build-time generator и пример-потребитель.

## Что делает плагин

- регистрирует permission hook `evaluate`;
- запрещает действие `read` для потенциально секретных путей;
- добавляет инструмент `kotlin_guard_inspect_path`, проверяющий путь без чтения файла;
- допускает обычные файлы-шаблоны `.env.example`, `.env.sample` и `.env.template`;
- распознаёт `.env`, варианты `.env.*` и стандартные имена приватных SSH-ключей;
- перед разрешением чтения проверяет также каноническую цель символической ссылки через `realpath`.

```text
OpenCode 2
   │
   ├── permission hook ──► Kotlin SensitivePathPolicy
   │
   └── kotlin_guard_inspect_path ──► Kotlin SensitivePathPolicy
                                      │
Kotlin/JS ── production ESM ── двухстрочный default-export adapter
```

## Почему есть ESM-адаптер

Kotlin/JS экспортирует `createPluginDefinition()` как именованный ESM-экспорт. OpenCode ожидает plugin definition в `default export`. Поэтому файл `plugin/index.mjs` только соединяет эти два контракта; поведение плагина, hook и tool реализованы в Kotlin.

## Архитектура библиотеки

```text
generator/ ──generates──► core/ ◄──depends── features/ ◄──depends── Secret Guard example
```

- `generator/` — Node/TypeScript AST/Karakum tooling; не входит в runtime и не публикуется;
- `:core` — только generated `external` declarations для shared, Promise, Effect и TUI API;
- `:features` — handwritten DSL, JSON Schema builders, typed tool helpers и lifecycle orchestration;
- корневой Kotlin target — Secret Guard как настоящий потребитель `:features`.

`generator` обрабатывает 57 собственных `.d.ts` закреплённого `@opencode-ai/plugin@0.0.0-beta-19271`, выпускает 340 Kotlin declarations и проверяет 208 direct exports и все re-export declarations. Транзитивные Effect/schema/client типы образуют явно зафиксированную opaque boundary вместо копирования всей Effect standard library.

Пример `features` DSL:

```kotlin
private external interface Input {
    val path: String
}

val plugin = opencodePlugin("example.guard") {
    permissions {
        evaluate { event ->
            if (event.action == "read") event.deny("Blocked")
        }
    }
    tools {
        transform {
            namespace("guard", "Guard tools")
            tool<Input, ToolResult>("inspect_path") {
                description = "Inspect a path"
                input(objectSchema {
                    string("path", required = true)
                    additionalProperties = false
                })
                execute { input, _ -> toolResult(content = input.path) }
            }
        }
    }
}
```

Generated-файлы находятся только в `core/build/generated/kotlin`. `features` не содержит generated-код, а `core` не содержит DSL.

## Требования

- JDK 17 или новее;
- Node.js;
- Bun — для установки закреплённой версии тестового OpenCode 2 CLI;
- Gradle отдельно не нужен: в репозитории есть wrapper.

Проверенные версии:

- Kotlin `2.3.21`;
- Gradle `9.3.1`;
- OpenCode 2 CLI и plugin SDK `0.0.0-beta-19271`;
- Karakum `1.0.0-alpha.112`;
- TypeScript `6.0.2`.

OpenCode 2 и его plugin API быстро меняются, поэтому версии CLI и SDK намеренно закреплены вместе без диапазона.

## Быстрый старт

```bash
bun install
bun run build
bun run check
```

Собранный пакет появится в `build/plugin/`:

```text
build/plugin/
├── index.mjs
├── package.json
└── kotlin/
    ├── opencode-kotlin-secret-guard.mjs
    └── opencode-kotlin-secret-guard.mjs.map
```

Проектный `opencode.jsonc` уже подключает этот каталог:

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "plugins": ["./build/plugin"]
}
```

Проверить загрузку вручную:

```bash
./node_modules/.bin/opencode2 plugin list
```

В таблице должна появиться строка с ID `panks.kotlin-secret-guard`, типом `local` и путём `build/plugin/index.mjs`.

Запустить OpenCode в проекте:

```bash
./node_modules/.bin/opencode2 .
```

После загрузки агенту доступен инструмент `kotlin_guard_inspect_path` с аргументом `path`.

## Команды

| Команда | Назначение |
|---|---|
| `./gradlew jsNodeTest` | Тесты Secret Guard example |
| `bun run generate:core` | Регенерация `:core` из закреплённого SDK |
| `bun run test:core` | Полнота exports/re-exports, nullability и детерминизм codegen |
| `./gradlew :core:compileKotlinJs` | Компиляция generated ABI |
| `./gradlew :features:jsNodeTest` | Тесты DSL, tools/schema builders и lifecycle cleanup |
| `./gradlew :core:publishToMavenLocal :features:publishToMavenLocal` | Публикация библиотек в Maven Local |
| `./gradlew assemblePlugin` | Production ESM и готовый каталог плагина |
| `./gradlew check` | Kotlin-тесты и smoke-import собранного ESM |
| `bun run verify:opencode` | Проверка появления плагина в реальном OpenCode 2 |
| `bun run check` | Полный локальный набор проверок |

## Структура

```text
generator/                                     — непубликуемый TypeScript/Karakum toolchain
core/                                          — generated ABI Kotlin/JS library
features/                                      — handwritten DSL и runtime helpers
src/jsMain/kotlin/.../SensitivePathPolicy.kt  — политика example-плагина
src/jsMain/kotlin/.../PathInspection.kt       — shared canonical path inspector example
src/jsMain/kotlin/.../OpenCodePlugin.kt       — Secret Guard на features DSL
src/jsTest/kotlin/...                         — integration tests example-плагина
plugin/index.mjs                              — default-export adapter
scripts/test-bindings-*.mjs                   — contract и deterministic проверки codegen
scripts/smoke-plugin.mjs                      — проверка импорта артефакта
scripts/verify-opencode.mjs                   — проверка настоящим CLI
```

## Ограничения безопасности

Это демонстрационный защитный слой, а не sandbox:

- hook контролирует разрешение `read`, но не анализирует произвольные shell-команды вроде `cat .env`;
- определение секретности основано на запрошенном пути и канонической цели символической ссылки, а не на содержимом;
- `realpath` закрывает обычные symlink aliases; ошибка canonicalization блокирует путь fail-closed;
- между `realpath` и фактическим чтением остаётся TOCTOU race: OpenCode hook передаёт путь, а не уже открытый файловый дескриптор, поэтому атомарно связать проверку и read невозможно;
- hardlink невозможно надёжно распознать по одному имени без отдельного inode/index policy;
- другие плагины и внешние процессы могут получать файлы иными способами;
- обычные `.env.*` шаблоны считаются безопасными только тогда, когда их каноническая цель также безопасна.

Для реального защищённого окружения этот hook следует сочетать с правилами permissions OpenCode, ограничением shell и изоляцией процесса.

## Лицензия

MIT.
