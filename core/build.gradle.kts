import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("multiplatform") version "2.3.21"
    `maven-publish`
}

group = "us.panks.opencode"
version = "0.2.0"

repositories {
    mavenCentral()
}

val generatorDirectory = rootProject.layout.projectDirectory.dir("generator")
val generatorNodeModules = generatorDirectory.dir("node_modules")

val installGeneratorTools by tasks.registering(Exec::class) {
    workingDir(generatorDirectory)
    commandLine("bun", "install", "--frozen-lockfile")
    inputs.files(
        generatorDirectory.file("package.json"),
        generatorDirectory.file("bun.lock"),
    )
    outputs.dir(generatorNodeModules)
}

val generateOpenCodeCore by tasks.registering(Exec::class) {
    dependsOn(installGeneratorTools)
    workingDir(generatorDirectory)
    commandLine("bun", "run", "generate")
    inputs.files(
        generatorDirectory.file("package.json"),
        generatorDirectory.file("bun.lock"),
        generatorDirectory.file("karakum.config.json"),
        fileTree(generatorDirectory.dir("scripts")) { include("**/*.mjs") },
        fileTree(generatorNodeModules.dir("@opencode-ai/plugin/dist")) { include("**/*.d.ts") },
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
            implementation(npm("@opencode-ai/plugin", "0.0.0-beta-19271"))
        }
    }
}

tasks.named("compileKotlinJs") {
    dependsOn(generateOpenCodeCore)
}

tasks.matching { it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn(generateOpenCodeCore)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = if (name == "kotlinMultiplatform") {
            "core"
        } else {
            "core-$name"
        }
    }
}
