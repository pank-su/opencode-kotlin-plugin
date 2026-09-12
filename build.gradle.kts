import org.gradle.api.tasks.Sync

plugins {
    kotlin("multiplatform") version "2.3.21"
}

group = "us.panks.opencode"
version = "0.1.0"

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
            implementation(project(":bindings"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
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
}
