# plugin

Базовый Promise-plugin DSL, lifecycle orchestration, coroutine Promise bridge и `@OpenCodePlugin`.

```kotlin
implementation("us.panks.opencode:plugin:0.2.0")
```

Обычно подключается транзитивно через `permissions` или `tools`. DSL setup registrations выполняются последовательно, rollback/cleanup — в обратном порядке и idempotent.
