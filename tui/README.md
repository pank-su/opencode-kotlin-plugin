# tui

Независимая capability для `@opencode-ai/plugin/tui`.

```kotlin
implementation("us.panks.opencode:tui:0.2.0")
```

DSL:

```kotlin
val plugin = tuiPlugin("example.tui") { context ->
    context.toast("Ready", variant = ToastVariants.SUCCESS)
}
```

Annotations:

```kotlin
import js.promise.await

@OpenCodeTuiPlugin("example.tui")
class UiPlugin {
    @TuiStart
    suspend fun start(context: TuiContext) {
        context.alert("Ready", "TUI plugin loaded").await()
    }
}
```

API включает toast, alert, confirm, prompt и generic select. `tuiPlugin` всегда принимает suspend setup и возвращает Promise на JavaScript-границе. `tuiPluginWithCleanup` принимает `suspend () -> Unit` cleanup и также адаптирует его в Promise. Nullable cancellation semantics upstream сохранены.
