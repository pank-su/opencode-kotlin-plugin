# OpenCode Kotlin Secret Guard

Экспериментальный, но рабочий плагин OpenCode 2, написанный на Kotlin/JS. Он показывает, как использовать Kotlin не только за MCP-границей, а непосредственно внутри процесса OpenCode через V2 plugin API.

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

## Сгенерированные Kotlin bindings

Модуль `:bindings` строит Kotlin declarations из закреплённого `@opencode-ai/plugin@0.0.0-beta-19271` по схеме, близкой к JetBrains `kotlin-wrappers`:

```text
57 upstream .d.ts
   → TypeScript AST compatibility projection
   → Karakum
   → 340 Kotlin external declarations
   → Kotlin compiler и manifest-проверка
```

Генератор охватывает все 208 именованных exports собственных declarations пакета: shared/core, Promise, Effect и TUI. Отдельный contract test проверяет, что export не потерян, пути сборочной машины не попали в результат и не осталось `unhandled import` comments. Повторная генерация проверяется побайтным SHA-256.

Граница типов намеренная: рекурсивный граф SDK содержит ещё 303 declaration-файла из `effect`, `@opencode-ai/schema`, `@opencode-ai/client`, AI SDK и других библиотек. Генератор не копирует целиком Effect standard library; сложные внешние и type-level конструкции представлены как opaque `Any?`. Все домены и операции plugin SDK остаются доступны, но для этих внешних значений возможен явный `unsafeCast` в handwritten adapters.

Generated-файлы находятся только в `bindings/build/generated/kotlin` и вручную не редактируются. Изменения вносятся в AST projector и Karakum config.

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
| `./gradlew jsNodeTest` | Kotlin/JS unit и contract tests |
| `bun run generate:bindings` | Полная регенерация Kotlin declarations из закреплённого SDK |
| `bun run test:bindings` | Полнота exports, детерминизм и отсутствие `dynamic` в production interop |
| `./gradlew :bindings:compileKotlinJs` | Компиляция всех generated declarations |
| `./gradlew assemblePlugin` | Production ESM и готовый каталог плагина |
| `./gradlew check` | Kotlin-тесты и smoke-import собранного ESM |
| `bun run verify:opencode` | Проверка появления плагина в реальном OpenCode 2 |
| `bun run check` | Полный локальный набор проверок |

## Структура

```text
bindings/                                      — production generator и отдельный Kotlin/JS-модуль
bindings/scripts/sanitize-full-sdk.mjs         — TypeScript AST compatibility projection
bindings/scripts/finalize-bindings.mjs         — coverage manifest и post-processing
src/jsMain/kotlin/.../SensitivePathPolicy.kt  — чистая политика путей
src/jsMain/kotlin/.../OpenCodePlugin.kt       — typed definition, hook и tool
src/jsTest/kotlin/...                         — Kotlin/JS-тесты
plugin/index.mjs                              — default-export adapter
scripts/test-bindings-*.mjs                   — contract и deterministic проверки codegen
scripts/smoke-plugin.mjs                      — проверка импорта артефакта
scripts/verify-opencode.mjs                   — проверка настоящим CLI
```

## Ограничения безопасности

Это демонстрационный защитный слой, а не sandbox:

- hook контролирует разрешение `read`, но не анализирует произвольные shell-команды вроде `cat .env`;
- определение секретности основано на запрошенном пути и канонической цели символической ссылки, а не на содержимом;
- `realpath` закрывает symlink aliases, но hardlink невозможно надёжно распознать по одному имени без отдельного inode/index policy;
- другие плагины и внешние процессы могут получать файлы иными способами;
- обычные `.env.*` шаблоны считаются безопасными только тогда, когда их каноническая цель также безопасна.

Для реального защищённого окружения этот hook следует сочетать с правилами permissions OpenCode, ограничением shell и изоляцией процесса.

## Лицензия

MIT.
