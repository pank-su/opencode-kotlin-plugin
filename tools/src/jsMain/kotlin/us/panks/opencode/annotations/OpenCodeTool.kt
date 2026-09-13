package us.panks.opencode.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class OpenCodeTool(
    val name: String = "",
    val description: String,
    val namespace: String = "",
    val namespaceDescription: String = "",
    val codeMode: Boolean = false,
)
