# processor

KSP2 processor для annotation-first OpenCode Kotlin API.

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.7"
}

dependencies {
    add("kspJs", "us.panks.opencode:processor:0.2.0")
}
```

Processor генерирует Kotlin/JS entrypoints из `@OpenCodePlugin`, `@OpenCodeTool`, `@OpenCodePermission`, `@OpenCodeTuiPlugin` и `@TuiStart`. Annotated handlers обязаны быть `suspend`; generated runtime всегда возвращает Promise. Это build-time dependency; в runtime bundle processor не включается.
