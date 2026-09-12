import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("multiplatform") version "2.3.21"
    `maven-publish`
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
            api(project(":core"))
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = if (name == "kotlinMultiplatform") {
            "opencode-kotlin-features"
        } else {
            "opencode-kotlin-features-$name"
        }
    }
}
