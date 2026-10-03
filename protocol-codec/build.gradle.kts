plugins { java; cult.`format-conventions`; id("com.gradleup.shadow") }
repositories { mavenCentral() }
java.toolchain.languageVersion.set(JavaLanguageVersion.of(25))
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

val codecs = layout.buildDirectory.dir("codecs")
val prepareCodecs by tasks.registering(Exec::class) {
    inputs.files("codecs-lock.json", rootProject.file("scripts/prepare-protocol-codecs.py"))
    outputs.files(codecs.map { it.file("ViaVersion.jar") }, codecs.map { it.file("ViaBackwards.jar") })
    commandLine("python3", rootProject.file("scripts/prepare-protocol-codecs.py"),
        "--lock", file("codecs-lock.json"), "--output", codecs.get().asFile)
    // Optional verified development cache; clean builds use the pinned public sources.
    val cache = rootProject.file(".ignored/compatibility-2026-10-03/translation-source-build")
    if (cache.isDirectory) args("--artifact-cache", cache)
}
dependencies {
    compileOnly(project(":protocol"))
    implementation(files(codecs.map { it.file("ViaVersion.jar") }, codecs.map { it.file("ViaBackwards.jar") }))
    compileOnly("io.netty:netty-transport:4.2.16.Final")
    compileOnly("io.netty:netty-codec-base:4.2.16.Final")
    compileOnly("com.google.guava:guava:33.5.0-jre")
    testImplementation(project(":protocol"))
    testImplementation("io.netty:netty-transport:4.2.16.Final")
    testImplementation("io.netty:netty-codec-base:4.2.16.Final")
    testImplementation("com.google.guava:guava:33.5.0-jre")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.compileJava { dependsOn(prepareCodecs) }
tasks.test { dependsOn(prepareCodecs); useJUnitPlatform() }
// This source set is a build tool and never enters the private codec or plugin jar.
val mappingGenerator = sourceSets.create("generator") {
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
}
tasks.register<JavaExec>("exportModelMappings") {
    dependsOn(tasks.classes, tasks.named(mappingGenerator.classesTaskName))
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    classpath = mappingGenerator.runtimeClasspath
    mainClass.set("ac.cult.cultac.codec.ExportModelMappings")
    args(rootProject.file("protocol/src/main/resources/ac/cult/cultac/protocol/model-mappings"))
}
tasks.shadowJar {
    archiveFileName.set("protocol-codecs.jar")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    doLast {
        check(ProcessBuilder("python3", rootProject.file("scripts/verify-no-bundled-minecraft.py").path,
            archiveFile.get().asFile.path).inheritIO().start().waitFor() == 0)
    }
}
