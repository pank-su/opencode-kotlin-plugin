package us.panks.opencode.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class OpenCodePlugin(
    val id: String,
    val entryPoint: String = "createPluginDefinition",
)
