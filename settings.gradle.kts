rootProject.name = "opencode-kotlin-secret-guard"

include(":core")
include(":plugin")
project(":plugin").projectDir = file("plugin-api")
include(":permissions")
include(":tools")
include(":tui")
include(":processor")
