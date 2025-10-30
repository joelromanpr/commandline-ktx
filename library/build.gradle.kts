plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish") version "0.34.0"
}

version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

mavenPublishing {
    coordinates(
        groupId = "io.github.joelromanpr",
        artifactId = "commandline-ktx",
        version = "1.0.0"
    )

    pom {
        name.set("commandline-ktx")
        description.set("A simple, modern, and type-safe command-line argument parser for Kotlin.")
    }
}
