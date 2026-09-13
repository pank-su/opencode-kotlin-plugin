# permissions

Опциональная capability для permission hooks.

```kotlin
implementation("us.panks.opencode:permissions:0.2.0")
```

DSL использует suspend handler:

```kotlin
val plugin = opencodePlugin("example.guard") {
    permissions {
        evaluate { event ->
            event.deny("Blocked")
        }
    }
}
```

Annotation-first:

```kotlin
@OpenCodePlugin("example.guard")
class GuardPlugin {
    @OpenCodePermission
    suspend fun evaluate(event: PermissionEvaluation) {
        event.deny("Blocked")
    }
}
```
