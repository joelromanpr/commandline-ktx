plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish") version "0.34.0"

}

version = "1.0.1-SNAPSHOT"

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
        version = version.toString()
    )

    pom {
        name.set("commandline-ktx")
        description.set("A simple, modern, and type-safe command-line argument parser for Kotlin.")
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

    // Configure signing for all publications
    signAllPublications()
}
