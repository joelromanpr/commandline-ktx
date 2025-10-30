
plugins {
    kotlin("jvm")
    application
}

group = "com.joelromanpr.commandline.ktx"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":library"))
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.joelromanpr.commandline.ktx.demo.DemoKt")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
