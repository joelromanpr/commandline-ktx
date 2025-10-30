plugins {
    kotlin("jvm") version "2.2.20" apply false
    id("com.diffplug.spotless") version "8.0.0"

}

repositories {
    mavenCentral()
}


subprojects {
    // Apply Spotless to every subproject
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        // Kotlin source
        kotlin {
            target("**/*.kt")
            targetExclude("${layout.buildDirectory}/**/*.kt", "**/build/**/*.kt")
            // KtLint reads .editorconfig for indentation and other rules
            licenseHeaderFile(rootProject.file("spotless/spotless.license.kt"))
            trimTrailingWhitespace()
            endWithNewline()
        }
        // Gradle Kotlin DSL in subprojects
        kotlinGradle {
            target("**/*.kts")
            targetExclude("${layout.buildDirectory}/**/*.kts", "**/build/**/*.kts")
            // KtLint reads .editorconfig for indentation and other rules
            trimTrailingWhitespace()
            endWithNewline()
        }
        // XML resources
        format("xml") {
            target("**/*.xml")
            targetExclude("**/build/**/*.xml")
            // Apply consistent indentation to XML
            leadingTabsToSpaces(4)
            // Look for the first XML tag that isn't a comment (<!--) or the xml declaration (<?xml)
            licenseHeaderFile(rootProject.file("spotless/spotless.license.xml"), "(<[^!?])")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
