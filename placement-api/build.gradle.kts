plugins { `java-library`; cult.`format-conventions` }
repositories { mavenCentral() }

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

// This boundary is deliberately JDK-only. Neither a proxy nor the isolated vanilla
// loader should need Paper, Minecraft, Netty, or another platform's class loader.
