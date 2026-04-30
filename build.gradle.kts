plugins {
    id("java")
    id("application")
}

group = "de.skerkewitz"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("de.skerkewitz.jcme.App")
    applicationName = "jcme"
    applicationDefaultJvmArgs = listOf(
        // Silence the JDK 24+ native-access warnings emitted by AppDirs/JNA on first call.
        "--enable-native-access=ALL-UNNAMED",
        // Force UTF-8 everywhere so German umlauts (and other non-ASCII chars in page
        // titles) don't get mangled by the platform default charset on Windows.
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "jconfluence-markdown-exporter",
            "Implementation-Version" to project.version,
            "Main-Class" to "de.skerkewitz.jcme.App"
        )
    }
}

dependencies {
    // CLI
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // JSON / YAML
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // HTML parsing (used in later phases)
    implementation("org.jsoup:jsoup:1.18.3")

    // Interactive prompts (later phases)
    implementation("org.jline:jline:3.27.1")

    // OS-specific app data dirs
    implementation("net.harawata:appdirs:1.4.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // Tests
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
