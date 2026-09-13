# core

Generated Kotlin/JS `external` declarations для закреплённого `@opencode-ai/plugin@0.0.0-beta-19271`.

```kotlin
implementation("us.panks.opencode:core:0.2.0")
```

Модуль не содержит DSL и handwritten runtime logic. Generated source создаётся `generator/` в `core/build/generated/kotlin`. Gradle metadata включает точную npm dependency на OpenCode SDK.

```bash
./gradlew :core:compileKotlinJs
./gradlew :core:publishToMavenLocal
```
