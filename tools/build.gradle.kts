import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("multiplatform") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    `maven-publish`
}

group = "us.panks.opencode"
version = "0.2.0"

repositories { mavenCentral() }

kotlin {
    js {
        useEsModules()
        nodejs()
        binaries.library()
        compilerOptions.target = "es2015"
    }
    sourceSets {
        jsMain.dependencies {
            api(project(":plugin"))
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":permissions"))
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = if (name == "kotlinMultiplatform") "tools" else "tools-$name"
    }
}
