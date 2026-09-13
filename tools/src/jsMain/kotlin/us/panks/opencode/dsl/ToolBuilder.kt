package us.panks.opencode.dsl

import js.promise.Promise
import kotlinx.serialization.KSerializer
import opencode.plugin.promise.Info
import opencode.plugin.promise.ToolContext
import us.panks.opencode.dsl.internal.jsObject

@OpenCodePluginDsl
public class ToolBuilder<Input, Output> internal constructor(
    private val name: String,
    serializer: KSerializer<Input>? = null,
) {
    public var description: String = ""

    private var inputSchema: JsonSchema? = serializer?.let { SerializedToolInput.schema(it.descriptor) }
    private var inputDecoder: (Any?) -> Input = serializer
        ?.let { inputSerializer -> { value -> SerializedToolInput.decode(inputSerializer, value) } }
        ?: { value -> value.unsafeCast<Input>() }
    private var outputSchema: JsonSchema? = null
    private var options: Any? = null
    private var executor: ((Any?, ToolContext) -> Promise<Any?>)? = null

    public fun input(schema: JsonSchema) {
        inputSchema = schema
    }

    public fun output(schema: JsonSchema) {
        outputSchema = schema
    }

    public fun options(block: ToolOptionsBuilder.() -> Unit) {
        options = ToolOptionsBuilder().apply(block).build()
    }

    public fun execute(block: suspend (Input, ToolContext) -> Output) {
        executor = { input, context ->
            openCodePromise {
                block(inputDecoder(input), context)
            }.then<Any?> { it }
        }
    }

    internal fun build(): Info<Any?, Any?> {
        require(name.isNotBlank()) { "Tool name must not be blank" }
        require(description.isNotBlank()) { "Tool description must not be blank" }
        val schema = requireNotNull(inputSchema) { "Tool input schema is required" }
        val run = requireNotNull(executor) { "Tool execute handler is required" }

        val values = mutableListOf<Pair<String, Any?>>(
            "name" to name,
            "description" to description,
            "input" to schema,
            "execute" to run,
        )
        outputSchema?.let { values += "output" to it }
        options?.let { values += "options" to it }
        return jsObject(*values.toTypedArray())
    }
}

@OpenCodePluginDsl
public class ToolOptionsBuilder internal constructor() {
    public var namespace: String? = null
    public var codeMode: Boolean? = null

    internal fun build(): Any? {
        val values = mutableListOf<Pair<String, Any?>>()
        namespace?.let {
            require(it.isNotBlank()) { "Tool option namespace must not be blank" }
            values += "namespace" to it
        }
        codeMode?.let { values += "codemode" to it }
        return jsObject<Any?>(*values.toTypedArray())
    }
}
