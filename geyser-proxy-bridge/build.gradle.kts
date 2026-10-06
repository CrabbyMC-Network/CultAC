plugins {
    `java-library`
    id("com.gradleup.shadow")
}
group = rootProject.group
version = rootProject.version
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-snapshots/")
    maven("https://repo.opencollab.dev/maven-releases/")
}
dependencies {
    implementation(project(":proxy-bridge-wire"))
    compileOnly("org.geysermc.geyser:core:2.11.3-20261004.183305-14")
    compileOnly(libs.fastutil)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.geysermc.geyser:core:2.11.3-20261004.183305-14")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testRuntimeOnly(libs.fastutil)
}
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("extension.yml") { expand("version" to project.version) }
}
tasks.shadowJar {
    archiveBaseName.set("cultac-geyser-proxy-bridge")
    archiveClassifier.set("")
    // Geyser-Velocity shades fastutil; Geyser method descriptors and registry casts must
    // reference its relocated copy. Nothing is bundled — fastutil stays compileOnly.
    relocate("it.unimi.dsi.fastutil", "org.geysermc.geyser.platform.velocity.shaded.it.unimi.dsi.fastutil")
}
tasks.build { dependsOn(tasks.shadowJar) }
