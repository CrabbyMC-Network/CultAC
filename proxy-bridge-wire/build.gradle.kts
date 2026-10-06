plugins { `java-library` }
group = rootProject.group
version = rootProject.version
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
repositories { mavenCentral() }
dependencies { testImplementation("junit:junit:4.13.2") }
