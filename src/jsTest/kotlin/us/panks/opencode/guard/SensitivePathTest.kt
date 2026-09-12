package us.panks.opencode.guard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitivePathTest {
    @Test
    fun blocksDotEnvFiles() {
        assertTrue(SensitivePathPolicy.isSensitive("/project/.env"))
    }

    @Test
    fun blocksDotEnvVariants() {
        assertTrue(SensitivePathPolicy.isSensitive("C:\\project\\.env.production"))
    }

    @Test
    fun allowsDocumentedEnvironmentTemplates() {
        assertFalse(SensitivePathPolicy.isSensitive("/project/.env.example"))
    }

    @Test
    fun blocksSshPrivateKeys() {
        assertTrue(SensitivePathPolicy.isSensitive("/home/user/.ssh/id_ed25519"))
    }
}
