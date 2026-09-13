package us.panks.opencode.dsl

import us.panks.opencode.dsl.internal.jsObject

public external interface JsonSchema

public fun objectSchema(
    block: ObjectSchemaBuilder.() -> Unit,
): JsonSchema = ObjectSchemaBuilder().apply(block).build()

@OpenCodePluginDsl
public class ObjectSchemaBuilder internal constructor() {
    private val properties = linkedMapOf<String, JsonSchema>()
    private val required = linkedSetOf<String>()

    public var additionalProperties: Boolean = true

    public fun string(
        name: String,
        description: String? = null,
        required: Boolean = false,
    ) {
        property(name, primitiveSchema("string", description), required)
    }

    public fun number(
        name: String,
        description: String? = null,
        required: Boolean = false,
    ) {
        property(name, primitiveSchema("number", description), required)
    }

    public fun boolean(
        name: String,
        description: String? = null,
        required: Boolean = false,
    ) {
        property(name, primitiveSchema("boolean", description), required)
    }

    public fun property(
        name: String,
        schema: JsonSchema,
        required: Boolean = false,
    ) {
        require(name.isNotBlank()) { "Schema property name must not be blank" }
        properties[name] = schema
        if (required) this.required += name else this.required -= name
    }

    internal fun build(): JsonSchema {
        val propertyObject = jsObject<Any?>(
            *properties.map { (name, schema) -> name to schema }.toTypedArray(),
        )
        return jsObject(
            "type" to "object",
            "properties" to propertyObject,
            "required" to required.toTypedArray(),
            "additionalProperties" to additionalProperties,
        )
    }
}

private fun primitiveSchema(
    type: String,
    description: String?,
): JsonSchema {
    val values = mutableListOf<Pair<String, Any?>>("type" to type)
    if (description != null) values += "description" to description
    return jsObject(*values.toTypedArray())
}
