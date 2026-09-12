plugins {
    kotlin("multiplatform") version "2.3.21"
}

group = "us.panks.opencode"
version = "0.1.0"

repositories {
    mavenCentral()
}

val installBindingsTools by tasks.registering(Exec::class) {
    workingDir(layout.projectDirectory)
    commandLine("bun", "install", "--frozen-lockfile")
    inputs.files("package.json", "bun.lock")
    outputs.dir("node_modules")
}

val generateOpenCodeBindings by tasks.registering(Exec::class) {
    dependsOn(installBindingsTools)
    workingDir(layout.projectDirectory)
    commandLine("bun", "run", "generate")
    inputs.files(
        "package.json",
        "bun.lock",
        "karakum.config.json",
        fileTree("scripts") { include("**/*.mjs") },
    )
    outputs.dir(layout.buildDirectory.dir("generated/kotlin"))
    outputs.file(layout.buildDirectory.file("bindings-manifest.json"))
}

kotlin {
    js {
        useEsModules()
        nodejs()
        binaries.library()
        compilerOptions {
            target = "es2015"
        }
    }

    sourceSets.named("jsMain") {
        kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
        dependencies {
            api("org.jetbrains.kotlin-wrappers:kotlin-js:2026.5.7")
        }
    }
}

tasks.named("compileKotlinJs") {
    dependsOn(generateOpenCodeBindings)
}
