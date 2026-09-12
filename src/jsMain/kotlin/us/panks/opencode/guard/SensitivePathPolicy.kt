package us.panks.opencode.guard

object SensitivePathPolicy {
    private val safeEnvironmentTemplates = setOf(".env.example", ".env.sample", ".env.template")
    private val privateKeyNames = setOf("id_rsa", "id_dsa", "id_ecdsa", "id_ed25519")

    fun isSensitive(path: String): Boolean {
        val name = path.replace('\\', '/').substringAfterLast('/').lowercase()
        if (name in safeEnvironmentTemplates) return false
        return name in privateKeyNames || name == ".env" || name.startsWith(".env.")
    }
}
