import org.gradle.api.tasks.Sync

plugins {
    kotlin("multiplatform") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("com.google.devtools.ksp") version "2.3.7"
}

group = "us.panks.opencode"
version = "0.2.0"

repositories {
    mavenCentral()
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

    sourceSets {
        jsMain.dependencies {
            implementation(project(":permissions"))
            implementation(project(":tools"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

dependencies {
    add("kspJs", project(":processor"))
    add("kspJsTest", project(":processor"))
}

val assemblePlugin by tasks.registering(Sync::class) {
    dependsOn("jsProductionLibraryCompileSync")
    from(layout.buildDirectory.dir("compileSync/js/main/productionLibrary/kotlin")) {
        include("opencode-kotlin-secret-guard.mjs")
        include("opencode-kotlin-secret-guard.mjs.map")
        into("kotlin")
    }
    from(layout.projectDirectory.dir("plugin"))
    into(layout.buildDirectory.dir("plugin"))
}

tasks.register<Exec>("smokePlugin") {
    dependsOn(assemblePlugin)
    commandLine("node", "scripts/smoke-plugin.mjs")
}

tasks.named("check") {
    dependsOn("smokePlugin")
    dependsOn(":core:check")
    dependsOn(":plugin:check")
    dependsOn(":permissions:check")
    dependsOn(":tools:check")
    dependsOn(":tui:check")
    dependsOn(":processor:check")
}
