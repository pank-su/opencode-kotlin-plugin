package us.panks.opencode.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter

private const val PLUGIN = "us.panks.opencode.annotations.OpenCodePlugin"
private const val PERMISSION = "us.panks.opencode.annotations.OpenCodePermission"
private const val TOOL = "us.panks.opencode.annotations.OpenCodeTool"
private const val TUI_PLUGIN = "us.panks.opencode.annotations.OpenCodeTuiPlugin"
private const val TUI_START = "us.panks.opencode.annotations.TuiStart"

public class OpenCodeProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        OpenCodeProcessor(environment.codeGenerator, environment.logger)
}

private class OpenCodeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private val generated = mutableMapOf<String, String>()

    override fun process(resolver: Resolver): List<KSClassDeclaration> {
        val pluginSymbols = resolver.getSymbolsWithAnnotation(PLUGIN).toList()
        val tuiSymbols = resolver.getSymbolsWithAnnotation(TUI_PLUGIN).toList()
        val symbols = pluginSymbols + tuiSymbols
        val deferred = symbols.filterNot { it.validate() }.filterIsInstance<KSClassDeclaration>()
        pluginSymbols.filter { it.validate() }.filterIsInstance<KSClassDeclaration>().forEach(::generatePlugin)
        tuiSymbols.filter { it.validate() }.filterIsInstance<KSClassDeclaration>().forEach(::generateTuiPlugin)
        return deferred
    }

    private fun generatePlugin(plugin: KSClassDeclaration) {
        val annotation = plugin.annotation(PLUGIN) ?: return
        val packageName = plugin.packageName.asString()
        val className = kotlinIdentifier(plugin.simpleName.asString())
        val pluginId = annotation.string("id")
        val entryPoint = annotation.string("entryPoint").ifBlank { "createPluginDefinition" }
        val key = "$packageName.$entryPoint"
        if (!reserveEntryPoint(key, plugin)) return

        validatePlugin(plugin, pluginId, entryPoint)
        val functions = plugin.getDeclaredFunctions().toList()
        val permissions = functions.filter { it.annotation(PERMISSION) != null }
            .sortedBy { it.simpleName.asString() }
        val tools = functions.mapNotNull { function ->
            function.annotation(TOOL)?.let { ToolHandler(function, it) }
        }.sortedBy { it.name.ifBlank { it.function.simpleName.asString() } }
        permissions.forEach(::validatePermission)
        tools.forEach(::validateTool)
        val duplicateTools = tools.groupBy { it.name.ifBlank { it.function.simpleName.asString() } }
            .filterValues { it.size > 1 }
            .keys
        check(duplicateTools.isEmpty(), plugin, "Duplicate @OpenCodeTool names: ${duplicateTools.sorted().joinToString()}")
        val conflictingNamespaces = tools.filter { it.namespace.isNotBlank() }
            .groupBy { it.namespace }
            .filterValues { handlers -> handlers.map { it.namespaceDescription }.filter { it.isNotBlank() }.distinct().size > 1 }
            .keys
        check(conflictingNamespaces.isEmpty(), plugin,
            "Conflicting namespace descriptions: ${conflictingNamespaces.sorted().joinToString()}")

        val source = buildSource(packageName, className, pluginId, entryPoint, permissions, tools)
        val dependencies = plugin.containingFile
            ?.let { Dependencies(aggregating = false, it) }
            ?: Dependencies(aggregating = false)
        codeGenerator.createNewFile(dependencies, packageName, "${entryPoint}OpenCodeGenerated").use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { it.write(source) }
        }
    }

    private fun generateTuiPlugin(plugin: KSClassDeclaration) {
        val annotation = plugin.annotation(TUI_PLUGIN) ?: return
        val packageName = plugin.packageName.asString()
        val className = kotlinIdentifier(plugin.simpleName.asString())
        val pluginId = annotation.string("id")
        val entryPoint = annotation.string("entryPoint").ifBlank { "createTuiPluginDefinition" }
        val key = "$packageName.$entryPoint"
        if (!reserveEntryPoint(key, plugin)) return

        validatePlugin(plugin, pluginId, entryPoint)
        val starts = plugin.getDeclaredFunctions().filter { it.annotation(TUI_START) != null }.toList()
        check(starts.size == 1, plugin, "@OpenCodeTuiPlugin requires exactly one @TuiStart function")
        val start = starts.single()
        validateHandlerVisibility(start)
        check(start.parameters.size == 1, start, "@TuiStart must accept one TuiContext")
        check(start.parameters.single().qualifiedType() in setOf(
            "opencode.plugin.tui.Context",
            "us.panks.opencode.tui.TuiContext",
        ), start, "@TuiStart parameter must be TuiContext")
        validateHandlerReturn(start, expectedType = "kotlin.Unit", label = "@TuiStart")

        val method = kotlinIdentifier(start.simpleName.asString())
        val source = buildString {
            if (packageName.isNotBlank()) {
                appendLine("package ${renderPackageName(packageName)}")
                appendLine()
            }
            appendLine("import us.panks.opencode.tui.*")
            appendLine()
            appendLine("@OptIn(ExperimentalJsExport::class)")
            appendLine("@JsExport")
            appendLine("public fun ${kotlinIdentifier(entryPoint)}(): TuiDefinition {")
            appendLine("    val instance = $className()")
            appendLine("    return tuiPlugin(${pluginId.quoted()}) { context -> instance.$method(context) }")
            appendLine("}")
        }
        val dependencies = plugin.containingFile
            ?.let { Dependencies(aggregating = false, it) }
            ?: Dependencies(aggregating = false)
        codeGenerator.createNewFile(dependencies, packageName, "${entryPoint}OpenCodeTuiGenerated").use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { it.write(source) }
        }
    }

    private fun buildSource(
        packageName: String,
        className: String,
        pluginId: String,
        entryPoint: String,
        permissions: List<KSFunctionDeclaration>,
        tools: List<ToolHandler>,
    ): String = buildString {
        if (packageName.isNotBlank()) {
            appendLine("package ${renderPackageName(packageName)}")
            appendLine()
        }
        appendLine("import opencode.plugin.promise.Plugin")
        appendLine("import us.panks.opencode.dsl.*")
        appendLine()
        appendLine("@OptIn(ExperimentalJsExport::class)")
        appendLine("@JsExport")
        appendLine("public fun ${kotlinIdentifier(entryPoint)}(): Plugin {")
        appendLine("    val instance = $className()")
        appendLine("    return opencodePlugin(${pluginId.quoted()}) {")
        if (permissions.isNotEmpty()) {
            appendLine("        permissions {")
            permissions.forEach { function ->
                val method = kotlinIdentifier(function.simpleName.asString())
                appendLine("            evaluate { event -> instance.$method(event) }")
            }
            appendLine("        }")
        }
        if (tools.isNotEmpty()) {
            appendLine("        tools {")
            appendLine("            transform {")
            tools.filter { it.namespace.isNotBlank() }
                .groupBy { it.namespace }
                .map { (namespace, handlers) ->
                    namespace to handlers.map { it.namespaceDescription }.firstOrNull { it.isNotBlank() }
                }
                .sortedBy { it.first }
                .forEach { (namespace, description) ->
                    val resolved = description ?: "Tools in namespace $namespace"
                    appendLine("                namespace(${namespace.quoted()}, ${resolved.quoted()})")
                }
            tools.forEach { tool -> appendTool(tool) }
            appendLine("            }")
            appendLine("        }")
        }
        appendLine("    }")
        appendLine("}")
    }

    private fun StringBuilder.appendTool(handler: ToolHandler) {
        val function = handler.function
        val rawMethod = function.simpleName.asString()
        val method = kotlinIdentifier(rawMethod)
        val inputType = function.parameters.first().type.resolve().render()
        val toolName = handler.name.ifBlank { rawMethod }
        appendLine("                tool<$inputType>(${toolName.quoted()}) {")
        appendLine("                    description = ${handler.description.quoted()}")
        if (handler.namespace.isNotBlank() || handler.codeMode) {
            appendLine("                    options {")
            if (handler.namespace.isNotBlank()) {
                appendLine("                        namespace = ${handler.namespace.quoted()}")
            }
            appendLine("                        codeMode = ${handler.codeMode}")
            appendLine("                    }")
        }
        val parameters = if (function.parameters.size == 2) "input, context" else "input, _"
        val arguments = if (function.parameters.size == 2) "input, context" else "input"
        appendLine("                    execute { $parameters -> instance.$method($arguments) }")
        appendLine("                }")
    }

    private fun reserveEntryPoint(key: String, plugin: KSClassDeclaration): Boolean {
        val owner = plugin.qualifiedName?.asString() ?: plugin.simpleName.asString()
        val previous = generated[key]
        if (previous == owner) return false
        check(previous == null, plugin, "Duplicate generated entryPoint $key from $previous and $owner")
        generated[key] = owner
        return true
    }

    private fun validatePlugin(plugin: KSClassDeclaration, id: String, entryPoint: String) {
        check(id.isNotBlank(), plugin, "Plugin id must not be blank")
        check(entryPoint.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")), plugin, "Invalid entryPoint: $entryPoint")
        check(plugin.classKind == ClassKind.CLASS, plugin, "Annotated plugin must be a class")
        check(plugin.parentDeclaration == null, plugin, "Annotated plugin must be top-level")
        check(Modifier.ABSTRACT !in plugin.modifiers, plugin, "Annotated plugin must not be abstract")
        check(Modifier.PRIVATE !in plugin.modifiers, plugin, "Annotated plugin must not be private")
        check(plugin.primaryConstructor?.parameters?.isEmpty() != false, plugin, "Annotated plugin requires a no-arg constructor")
    }

    private fun validatePermission(function: KSFunctionDeclaration) {
        validateHandlerVisibility(function)
        check(function.parameters.size == 1, function, "@OpenCodePermission function must accept one PermissionEvaluation")
        check(function.parameters.firstOrNull()?.qualifiedType() == "opencode.plugin.promise.PermissionEvaluation", function,
            "@OpenCodePermission parameter must be PermissionEvaluation")
        validateHandlerReturn(function, expectedType = "kotlin.Unit", label = "@OpenCodePermission")
    }

    private fun validateTool(handler: ToolHandler) {
        val function = handler.function
        validateHandlerVisibility(function)
        check(handler.description.isNotBlank(), function, "@OpenCodeTool description must not be blank")
        check(function.parameters.size in 1..2, function, "@OpenCodeTool must accept input and optional ToolContext")
        if (function.parameters.size == 2) {
            check(function.parameters[1].qualifiedType() == "opencode.plugin.promise.ToolContext", function,
                "Second @OpenCodeTool parameter must be ToolContext")
        }
        val inputDeclaration = function.parameters.first().type.resolve().declaration as? KSClassDeclaration
        check(inputDeclaration?.annotation("kotlinx.serialization.Serializable") != null, function,
            "@OpenCodeTool input must be @Serializable")
        validateSerializableInput(function.parameters.first().type.resolve(), function)
        validateHandlerReturn(function, expectedType = "us.panks.opencode.dsl.ToolResult", label = "@OpenCodeTool")
    }

    private fun validateHandlerReturn(
        function: KSFunctionDeclaration,
        expectedType: String,
        label: String,
    ) {
        val returnType = function.returnType?.resolve()
        val directType = returnType?.declaration?.qualifiedName?.asString()
        check(Modifier.SUSPEND in function.modifiers, function, "$label handler must be suspend")
        check(directType == expectedType && returnType.nullability != Nullability.NULLABLE, function,
            "$label suspend handler must return $expectedType directly")
    }

    private fun validateSerializableInput(type: KSType, symbol: KSNode) {
        check(type.arguments.isEmpty(), symbol,
            "Generic @OpenCodeTool root inputs are not supported by annotation mode; use the DSL serializer overload")
        validateSerializableType(type, symbol, linkedSetOf())
    }

    private fun validateSerializableType(
        type: KSType,
        symbol: KSNode,
        activeTypes: MutableSet<String>,
    ) {
        check(!type.hasUnsupportedSerializationAnnotation() && !type.hasCustomSerializer(), symbol,
            "Type-use contextual/polymorphic/custom serializer is not supported in annotation tool inputs")
        val name = type.declaration.qualifiedName?.asString()
            ?: run {
                check(false, symbol, "Tool input type must have a qualified declaration")
                return
            }
        if (name in supportedScalarTypes) return
        if (name in supportedListTypes) {
            val element = type.arguments.singleOrNull()?.type?.resolve()
                ?: run {
                    check(false, symbol, "Collection tool input must declare one element type")
                    return
                }
            validateSerializableType(element, symbol, activeTypes)
            return
        }
        if (name in supportedMapTypes) {
            val arguments = type.arguments.mapNotNull { it.type?.resolve() }
            check(arguments.size == 2, symbol, "Map tool input must declare key and value types")
            validateSerializableType(arguments[0], symbol, activeTypes)
            check(arguments[0].declaration.qualifiedName?.asString() == "kotlin.String", symbol,
                "Only maps with String keys are supported in annotation tool inputs")
            validateSerializableType(arguments[1], symbol, activeTypes)
            return
        }

        val declaration = type.declaration as? KSClassDeclaration
            ?: run {
                check(false, symbol, "Unsupported serializable tool input type: $name")
                return
            }
        check(!declaration.hasUnsupportedSerializationAnnotation() &&
            declaration.containingFile?.hasUnsupportedSerializationAnnotation() != true, symbol,
            "Polymorphic/contextual tool input is not supported in annotation mode: $name")
        val serializable = declaration.annotation("kotlinx.serialization.Serializable")
            ?: run {
                check(false, symbol, "Nested tool input type must be @Serializable: $name")
                return
            }
        check(serializable.arguments.none { it.name?.asString() == "with" }, symbol,
            "Custom serializers are not supported in annotation tool inputs: $name")
        check(Modifier.SEALED !in declaration.modifiers && Modifier.ABSTRACT !in declaration.modifiers, symbol,
            "Sealed or abstract tool input is not supported in annotation mode: $name")
        check(Modifier.VALUE !in declaration.modifiers, symbol,
            "Value-class tool input is not supported in annotation mode: $name")
        check(declaration.typeParameters.isEmpty(), symbol,
            "Generic serializable classes are not supported in annotation mode: $name")
        check(declaration.classKind !in setOf(ClassKind.INTERFACE, ClassKind.ANNOTATION_CLASS), symbol,
            "Interface/annotation tool input is not supported in annotation mode: $name")
        if (declaration.classKind in setOf(ClassKind.ENUM_CLASS, ClassKind.OBJECT)) return

        check(activeTypes.add(name), symbol, "Recursive tool input is not supported in annotation mode: $name")
        try {
            declaration.getDeclaredProperties().forEach { property ->
                if (property.hasAnnotation("kotlinx.serialization.Transient")) return@forEach
                check(!property.hasUnsupportedSerializationAnnotation() &&
                    !property.type.hasUnsupportedSerializationAnnotation() &&
                    !property.hasCustomSerializer() &&
                    !property.type.hasCustomSerializer(), property,
                    "Contextual/polymorphic/custom property is not supported in annotation tool inputs: ${property.simpleName.asString()}")
                validateSerializableType(property.type.resolve(), property, activeTypes)
            }
            declaration.superTypes
                .map { it.resolve() }
                .filter { superType ->
                    val superDeclaration = superType.declaration as? KSClassDeclaration
                    superDeclaration?.classKind == ClassKind.CLASS &&
                        superDeclaration.qualifiedName?.asString() != "kotlin.Any"
                }
                .forEach { superType -> validateSerializableType(superType, symbol, activeTypes) }
        } finally {
            activeTypes.remove(name)
        }
    }

    private fun validateHandlerVisibility(function: KSFunctionDeclaration) {
        check(Modifier.PRIVATE !in function.modifiers, function, "Annotated handler must not be private")
        check(Modifier.PROTECTED !in function.modifiers, function, "Annotated handler must not be protected")
        check(Modifier.ABSTRACT !in function.modifiers, function, "Annotated handler must not be abstract")
    }

    private fun check(condition: Boolean, symbol: com.google.devtools.ksp.symbol.KSNode, message: String) {
        if (!condition) {
            logger.error(message, symbol)
            throw IllegalStateException(message)
        }
    }
}

private val supportedScalarTypes = setOf(
    "kotlin.String", "kotlin.Char", "kotlin.Boolean",
    "kotlin.Byte", "kotlin.Short", "kotlin.Int", "kotlin.Long",
    "kotlin.Float", "kotlin.Double",
)

private val supportedListTypes = setOf(
    "kotlin.collections.List", "kotlin.collections.MutableList",
    "kotlin.collections.Set", "kotlin.collections.MutableSet",
    "kotlin.Array",
)

private val supportedMapTypes = setOf(
    "kotlin.collections.Map", "kotlin.collections.MutableMap",
)

private val unsupportedSerializationAnnotations = setOf(
    "kotlinx.serialization.Contextual",
    "kotlinx.serialization.Polymorphic",
    "kotlinx.serialization.UseContextualSerialization",
    "kotlinx.serialization.UseSerializers",
)

private fun KSAnnotated.hasAnnotation(name: String): Boolean =
    annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == name }

private fun KSAnnotated.hasUnsupportedSerializationAnnotation(): Boolean =
    annotations.any {
        it.annotationType.resolve().declaration.qualifiedName?.asString() in unsupportedSerializationAnnotations
    }

private fun KSType.hasUnsupportedSerializationAnnotation(): Boolean =
    annotations.any {
        it.annotationType.resolve().declaration.qualifiedName?.asString() in unsupportedSerializationAnnotations
    }

private fun KSAnnotated.hasCustomSerializer(): Boolean =
    annotations.any { annotation -> annotation.isCustomSerializerAnnotation() }

private fun KSType.hasCustomSerializer(): Boolean =
    annotations.any { annotation -> annotation.isCustomSerializerAnnotation() }

private fun KSAnnotation.isCustomSerializerAnnotation(): Boolean =
    annotationType.resolve().declaration.qualifiedName?.asString() ==
        "kotlinx.serialization.Serializable" &&
        arguments.any { it.name?.asString() == "with" }

private data class ToolHandler(
    val function: KSFunctionDeclaration,
    val annotation: KSAnnotation,
) {
    val name: String get() = annotation.string("name")
    val description: String get() = annotation.string("description")
    val namespace: String get() = annotation.string("namespace")
    val namespaceDescription: String get() = annotation.string("namespaceDescription")
    val codeMode: Boolean get() = annotation.boolean("codeMode")
}

private fun KSClassDeclaration.annotation(name: String): KSAnnotation? =
    annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == name }

private fun KSFunctionDeclaration.annotation(name: String): KSAnnotation? =
    annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == name }

private fun KSAnnotation.string(name: String): String =
    arguments.firstOrNull { it.name?.asString() == name }?.value as? String ?: ""

private fun KSAnnotation.boolean(name: String): Boolean =
    arguments.firstOrNull { it.name?.asString() == name }?.value as? Boolean ?: false

private fun KSValueParameter.qualifiedType(): String? =
    type.resolve().declaration.qualifiedName?.asString()

private fun com.google.devtools.ksp.symbol.KSType.render(): String {
    val declarationName = declaration.renderReference()
    val typeArguments = if (arguments.isEmpty()) {
        ""
    } else {
        arguments.joinToString(prefix = "<", postfix = ">") { argument ->
            val type = argument.type?.resolve()?.render() ?: return@joinToString "*"
            when (argument.variance) {
                Variance.COVARIANT -> "out $type"
                Variance.CONTRAVARIANT -> "in $type"
                Variance.STAR -> "*"
                Variance.INVARIANT -> type
            }
        }
    }
    val nullable = if (nullability == Nullability.NULLABLE) "?" else ""
    return declarationName + typeArguments + nullable
}

private fun KSDeclaration.renderReference(): String {
    val parent = parentDeclaration
    if (parent != null) return parent.renderReference() + "." + kotlinIdentifier(simpleName.asString())
    val packageName = packageName.asString()
    val name = kotlinIdentifier(simpleName.asString())
    return if (packageName.isBlank()) name else renderPackageName(packageName) + "." + name
}

private fun renderPackageName(packageName: String): String =
    packageName.split('.').joinToString(".") { kotlinIdentifier(it) }

private fun String.quoted(): String = kotlinStringLiteral(this)

internal fun kotlinStringLiteral(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> append("\\$")
            else -> {
                val code = character.code
                if (
                    code < 0x20 ||
                    code in 0x7f..0x9f ||
                    code in 0xd800..0xdfff ||
                    code == 0x2028 ||
                    code == 0x2029
                ) {
                    append("\\u")
                    append(code.toString(16).uppercase().padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}

private val kotlinKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
    "in", "interface", "is", "null", "object", "package", "return", "super", "this",
    "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
)

internal fun kotlinIdentifier(value: String): String {
    if (value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && value !in kotlinKeywords) return value
    require(value.isNotEmpty() && value.none {
        val code = it.code
        it == '`' || code < 0x20 || code in 0x7f..0x9f ||
            code in 0xd800..0xdfff || code == 0x2028 || code == 0x2029
    }) {
        "Identifier cannot be represented safely in generated Kotlin: $value"
    }
    return "`$value`"
}
