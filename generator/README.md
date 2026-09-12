# OpenCode Kotlin generator

Build-time tooling для генерации `:core`. Этот каталог не является Gradle-модулем, Maven-артефактом или runtime dependency.

## Pipeline

```text
@opencode-ai/plugin/dist/**/*.d.ts
  → check-upstream-contract.mjs
  → sanitize-full-sdk.mjs
  → Karakum
  → finalize-bindings.mjs
  → ../core/build/generated/kotlin
```

Версии `@opencode-ai/plugin`, TypeScript и Karakum закреплены в `package.json` и `bun.lock`.

AST projection сохраняет API-поверхность package, разворачивает наследуемые domain members и нормализует TypeScript-конструкции без прямого Kotlin-представления. Manifest строится от исходных `.d.ts`, учитывает direct declarations и все re-exports, использует POSIX-normalized paths и фиксирует intentional opaque external boundary.

## Команды

Из корня:

```bash
bun run generate:core
bun run test:core
./gradlew :core:compileKotlinJs
```

Обычно generator вызывается автоматически задачей `:core:generateOpenCodeCore`.
