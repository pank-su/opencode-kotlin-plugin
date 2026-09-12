# OpenCode Kotlin/JS bindings

Этот модуль генерирует Kotlin `external` declarations для `@opencode-ai/plugin@0.0.0-beta-19271`.

## Pipeline

```text
@opencode-ai/plugin/dist/**/*.d.ts
  → scripts/check-upstream-contract.mjs
  → scripts/sanitize-full-sdk.mjs
  → Karakum 1.0.0-alpha.112
  → scripts/finalize-bindings.mjs
  → build/generated/kotlin
```

`sanitize-full-sdk.mjs` разворачивает наследуемые `Pick`/API members и нормализует конструкции TypeScript, которым нет прямого представления в Kotlin. Promise, Effect, TUI и shared declarations выводятся в разные Kotlin packages.

## Гарантии

- обрабатываются все 57 собственных declaration-файлов package;
- manifest сверяет все 208 именованных exports с generated Kotlin declarations;
- generated output компилируется отдельным Kotlin/JS-модулем;
- SDK, Karakum, TypeScript и npm graph закреплены lockfile;
- абсолютные пути, временные source paths и `unhandled import` comments запрещены contract test;
- повторная генерация должна давать тот же SHA-256.

## Opaque boundary

Транзитивный declaration graph включает ещё 303 файла и около 299 тысяч строк из Effect, schema, client, AI SDK и вспомогательных библиотек. Они не дублируются этим модулем целиком. Сложные внешние или чисто type-level значения преобразуются в `Any?`, а их количество фиксируется в `build/bindings-manifest.json`.

Это полное покрытие API-поверхности пакета plugin, но не полноценный Kotlin wrapper всей Effect standard library.

## Команды

Из корня проекта:

```bash
bun run generate:bindings
bun run test:bindings
./gradlew :bindings:compileKotlinJs
```

Generated-файлы находятся в `build/` и вручную не редактируются.
