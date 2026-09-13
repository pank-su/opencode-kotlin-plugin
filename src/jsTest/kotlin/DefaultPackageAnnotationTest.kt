import kotlinx.serialization.Serializable
import us.panks.opencode.annotations.OpenCodePlugin
import us.panks.opencode.annotations.OpenCodeTool
import us.panks.opencode.dsl.ToolResult
import us.panks.opencode.dsl.toolResult
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
internal data class DefaultPackageInput(val value: String)

@OpenCodePlugin(
    id = "test.default-package",
    entryPoint = "createDefaultPackageFixture",
)
internal class DefaultPackagePlugin {
    @OpenCodeTool(name = "identity", description = "Return the input")
    suspend fun identity(input: DefaultPackageInput): ToolResult = toolResult(content = input.value)
}

@Serializable
internal data class `Keyword Input`(val value: String)

@OpenCodePlugin(
    id = "test.escaped-identifiers",
    entryPoint = "createEscapedIdentifierFixture",
)
internal class `when` {
    @OpenCodeTool(name = "escaped", description = "Escaped identifiers")
    suspend fun `is`(input: `Keyword Input`): ToolResult = toolResult(content = input.value)
}

class DefaultPackageAnnotationTest {
    @Test
    fun generatesEntrypointWithoutInvalidPackageDirective() {
        assertEquals("test.default-package", createDefaultPackageFixture().id)
    }

    @Test
    fun generatesEscapedKotlinIdentifiers() {
        assertEquals("test.escaped-identifiers", createEscapedIdentifierFixture().id)
    }
}
