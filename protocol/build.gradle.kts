plugins {
    cult.`base-conventions`
}

repositories { mavenCentral() }

// Keep the shared toolchain/style conventions without its Lombok classpath.
configurations.configureEach {
    setExtendsFrom(extendsFrom.filterNot { it.name == "lombok" })
}

dependencies {
    // Paper 26.3; only the ByteBuf API is used, also provided by older Papers.
    compileOnly("io.netty:netty-buffer:4.2.16.Final")
    testImplementation("io.netty:netty-buffer:4.2.16.Final")
    testImplementation(testlibs.junitJupiter)
    testRuntimeOnly(testlibs.junitPlatformLauncher)
}

tasks.test { useJUnitPlatform() }

sourceSets.test { java.srcDir("src/fixtures/java") }
