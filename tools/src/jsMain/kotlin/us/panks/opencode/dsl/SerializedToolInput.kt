package us.panks.opencode.dsl

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import us.panks.opencode.dsl.internal.jsObject

@OptIn(ExperimentalSerializationApi::class)
internal object SerializedToolInput {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = true
        coerceInputValues = false
        useAlternativeNames = false
    }

    fun <Input> decode(serializer: KSerializer<Input>, value: Any?): Input =
        json.decodeFromDynamic(serializer, value)

    fun schema(descriptor: SerialDescriptor): JsonSchema =
        schemaValue(descriptor, linkedSetOf())

    private fun schemaValue(
        descriptor: SerialDescriptor,
        activeDescriptors: MutableSet<String>,
    ): JsonSchema {
        val identity = descriptor.serialName
        check(activeDescriptors.add(identity)) {
            "Recursive serial descriptor is not supported for tool input: $identity"
        }
        try {
            val base = when (val kind = descriptor.kind) {
                PrimitiveKind.STRING, PrimitiveKind.CHAR -> typeSchema("string")
                PrimitiveKind.BOOLEAN -> typeSchema("boolean")
                PrimitiveKind.BYTE,
                PrimitiveKind.SHORT,
                PrimitiveKind.INT,
                PrimitiveKind.LONG,
                -> typeSchema("integer")
                PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> typeSchema("number")
                SerialKind.ENUM -> enumSchema(descriptor)
                StructureKind.CLASS, StructureKind.OBJECT -> objectSchema(descriptor, activeDescriptors)
                StructureKind.LIST -> jsObject(
                    "type" to "array",
                    "items" to schemaValue(descriptor.getElementDescriptor(0), activeDescriptors),
                )
                StructureKind.MAP -> mapSchema(descriptor, activeDescriptors)
                else -> error("Unsupported serial kind ${kind::class.simpleName} for ${descriptor.serialName}")
            }
            if (!descriptor.isNullable) return base
            return jsObject(
                "anyOf" to arrayOf(
                    base,
                    jsObject<JsonSchema>("type" to "null"),
                ),
            )
        } finally {
            activeDescriptors.remove(identity)
        }
    }

    private fun objectSchema(
        descriptor: SerialDescriptor,
        activeDescriptors: MutableSet<String>,
    ): JsonSchema {
        val properties = mutableListOf<Pair<String, Any?>>()
        val required = mutableListOf<String>()
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            properties += name to schemaValue(descriptor.getElementDescriptor(index), activeDescriptors)
            if (!descriptor.isElementOptional(index)) required += name
        }
        return jsObject(
            "type" to "object",
            "properties" to jsObject<Any?>(*properties.toTypedArray()),
            "required" to required.toTypedArray(),
            "additionalProperties" to false,
        )
    }

    private fun enumSchema(descriptor: SerialDescriptor): JsonSchema = jsObject(
        "type" to "string",
        "enum" to Array(descriptor.elementsCount, descriptor::getElementName),
    )

    private fun mapSchema(
        descriptor: SerialDescriptor,
        activeDescriptors: MutableSet<String>,
    ): JsonSchema {
        val key = descriptor.getElementDescriptor(0)
        require(key.kind == PrimitiveKind.STRING) {
            "Only maps with String keys are supported in tool inputs: ${descriptor.serialName}"
        }
        return jsObject(
            "type" to "object",
            "additionalProperties" to schemaValue(descriptor.getElementDescriptor(1), activeDescriptors),
        )
    }

    private fun typeSchema(type: String): JsonSchema = jsObject("type" to type)
}
