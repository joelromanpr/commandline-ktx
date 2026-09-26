plugins {
    kotlin("jvm")
    `java-library`
    id("com.vanniktech.maven.publish") version "0.34.0"
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":library"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

version = "2.0.1-SNAPSHOT"
mavenPublishing {
    coordinates(
        groupId = "io.github.joelromanpr",
        artifactId = "commandline-ktx-mcp-bridge",
        version = version.toString()
    )

    pom {
        name.set("commandline-ktx-mcp-bridge")
        description.set("A small MCP tool bridge for commandline-ktx without an MCP runtime dependency.")
        url.set("https://github.com/joelromanpr/commandline-ktx")

        licenses {
            license {
                name.set("The MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("joelromanpr")
                name.set("Joel Roman")
                email.set("contact@joelromanpr.com")
            }
        }

        scm {
            url.set("https://github.com/joelromanpr/commandline-ktx")
            connection.set("scm:git:git://github.com/joelromanpr/commandline-ktx.git")
            developerConnection.set("scm:git:ssh://git@github.com/joelromanpr/commandline-ktx.git")
        }
    }

    signAllPublications()
}
