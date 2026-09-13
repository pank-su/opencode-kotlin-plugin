package us.panks.opencode.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class OpenCodeTuiPlugin(
    val id: String,
    val entryPoint: String = "createTuiPluginDefinition",
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class TuiStart
