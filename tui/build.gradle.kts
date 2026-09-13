import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("multiplatform") version "2.3.21"
    id("com.google.devtools.ksp")
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
            api(project(":core"))
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        jsTest.dependencies { implementation(kotlin("test")) }
    }
}

dependencies {
    add("kspJsTest", project(":processor"))
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = if (name == "kotlinMultiplatform") "tui" else "tui-$name"
    }
}
