package us.panks.opencode.tui

import js.promise.Promise

public data class TuiSelectOption<Value>(
    val title: String,
    val value: Value,
    val description: String? = null,
    val footer: String? = null,
    val category: String? = null,
    val disabled: Boolean = false,
)

public fun TuiContext.alert(
    title: String,
    message: String,
): Promise<Unit> {
    require(title.isNotBlank()) { "Dialog title must not be blank" }
    val options: dynamic = js("({})")
    options.title = title
    options.message = message
    return ui.dialog.alert(options).then<Unit> { Unit }
}

public fun TuiContext.confirm(
    title: String,
    message: String,
    confirmLabel: String? = null,
    cancelLabel: String? = null,
): Promise<Boolean?> {
    require(title.isNotBlank()) { "Dialog title must not be blank" }
    val options: dynamic = js("({})")
    options.title = title
    options.message = message
    if (confirmLabel != null || cancelLabel != null) {
        val label: dynamic = js("({})")
        if (confirmLabel != null) label.confirm = confirmLabel
        if (cancelLabel != null) label.cancel = cancelLabel
        options.label = label
    }
    return ui.dialog.confirm(options)
}

public fun TuiContext.prompt(
    title: String,
    description: String? = null,
    placeholder: String? = null,
    value: String? = null,
): Promise<String?> {
    require(title.isNotBlank()) { "Dialog title must not be blank" }
    val options: dynamic = js("({})")
    options.title = title
    if (description != null) options.description = description
    if (placeholder != null) options.placeholder = placeholder
    if (value != null) options.value = value
    return ui.dialog.prompt(options)
}

public fun <Value> TuiContext.select(
    title: String,
    options: List<TuiSelectOption<Value>>,
    placeholder: String? = null,
    current: Value? = null,
): Promise<Value?> {
    require(title.isNotBlank()) { "Dialog title must not be blank" }
    require(options.isNotEmpty()) { "Select dialog requires at least one option" }
    val rawOptions = options.map { option ->
        val raw: dynamic = js("({})")
        raw.title = option.title
        raw.value = option.value
        if (option.description != null) raw.description = option.description
        if (option.footer != null) raw.footer = option.footer
        if (option.category != null) raw.category = option.category
        if (option.disabled) raw.disabled = true
        raw
    }.toTypedArray()
    val input: dynamic = js("({})")
    input.title = title
    input.options = rawOptions
    if (placeholder != null) input.placeholder = placeholder
    if (current != null) input.current = current
    return ui.dialog.select(input)
}
