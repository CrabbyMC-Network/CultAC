plugins {
    id("com.diffplug.spotless")
}

// Spotless configuration shared by every module. Only sources owned by the module
// are formatted; Spotless rejects targets outside the project directory, such as
// shared validation fixtures that other projects add as source directories.
spotless {
    java {
        target("src/**/*.java")
        // yes... it is THAT palantir
        palantirJavaFormat("2.101.0")
    }

    kotlinGradle {
        endWithNewline()
        indentWithSpaces(4)
        trimTrailingWhitespace()
    }
}

pluginManager.withPlugin("base") {
    // Ensure spotlessApply runs before build
    tasks.named("build") { dependsOn(tasks.named("spotlessApply")) }
}
