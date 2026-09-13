package us.panks.opencode.tui

public object ToastVariants {
    public const val INFO: String = "info"
    public const val SUCCESS: String = "success"
    public const val WARNING: String = "warning"
    public const val ERROR: String = "error"
}

public fun TuiContext.toast(
    message: String,
    title: String? = null,
    variant: String? = null,
    duration: Double? = null,
) {
    require(message.isNotBlank()) { "Toast message must not be blank" }
    val options: dynamic = js("({})")
    options.message = message
    if (title != null) options.title = title
    if (variant != null) {
        require(variant in setOf("info", "success", "warning", "error")) { "Invalid toast variant: $variant" }
        options.variant = variant
    }
    if (duration != null) options.duration = duration
    ui.toast.show(options)
}
