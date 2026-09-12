# OpenCode Kotlin Core

Минимальный Kotlin/JS ABI-слой над `@opencode-ai/plugin@0.0.0-beta-19271`.

- содержит только generated `external` declarations;
- не содержит DSL, бизнес-логики и Node tooling;
- разделяет shared, Promise, Effect и TUI packages;
- генерируется sibling-каталогом `generator/`;
- компилируется и публикуется независимо.

## Maven coordinates

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("us.panks.opencode:opencode-kotlin-core:0.1.0")
}
```

JS target artifact публикуется как `opencode-kotlin-core-js` и выбирается через Gradle module metadata.

Итоговый JS package также должен содержать совместимую runtime dependency:

```json
{
  "dependencies": {
    "@opencode-ai/plugin": "0.0.0-beta-19271"
  }
}
```

Generated declarations используют `@JsModule("@opencode-ai/plugin/...")`; Maven artifact предоставляет Kotlin ABI, но не встраивает npm runtime SDK.

## Команды

```bash
./gradlew :core:compileKotlinJs
./gradlew :core:publishToMavenLocal
```

Generated source находится в `core/build/generated/kotlin` и вручную не редактируется.
