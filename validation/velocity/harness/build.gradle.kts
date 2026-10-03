plugins { java; cult.`format-conventions` }
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
java.toolchain.languageVersion.set(JavaLanguageVersion.of(25))
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0")
}
tasks.jar { archiveFileName.set("CultAC-smoke-observer.jar") }
