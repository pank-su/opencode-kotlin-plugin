import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "us.panks.opencode"
version = "0.2.0"

repositories { mavenCentral() }

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.7")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("processor") {
            from(components["java"])
            artifactId = "processor"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
