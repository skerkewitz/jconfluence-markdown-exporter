plugins {
    id("java")
    id("application")
    // Native-image build via the GraalVM native-build-tools plugin.
    // See https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html
    id("org.graalvm.buildtools.native") version "0.10.4"
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

    // HTML parsing
    implementation("org.jsoup:jsoup:1.18.3")

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

// ----------------------------------------------------------------------------
// GraalVM native-image
// ----------------------------------------------------------------------------
//
// Build with `./gradlew nativeCompile`. Requires a GraalVM JDK 21+ on PATH (or
// JAVA_HOME) — Liberica NIK, Mandrel, or graalvm-ce all work. Output lands at
// build/native/nativeCompile/jcme(.exe).
//
// To regenerate reflection/resource metadata for our own code (e.g. after
// adding new Jackson-bound records), run the JVM under the tracing agent:
//   ./gradlew -Pagent run --args="config list"
// then commit the files written under src/main/resources/META-INF/native-image.
// ----------------------------------------------------------------------------
graalvmNative {
    binaries {
        named("main") {
            imageName.set("jcme")
            mainClass.set("de.skerkewitz.jcme.App")
            // Don't fall back to the JVM if native compilation hits something it
            // can't handle — fail loudly instead so we know to add metadata.
            buildArgs.add("--no-fallback")
            // We make HTTPS requests to Confluence; make sure the URL handler
            // is bundled. http kept too in case someone uses it on a LAN install.
            buildArgs.add("--enable-url-protocols=https,http")
            // Helpful when something at runtime is missing reflection metadata.
            buildArgs.add("-H:+UnlockExperimentalVMOptions")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            // Force UTF-8 inside the native binary too.
            buildArgs.add("-Dfile.encoding=UTF-8")
            buildArgs.add("-Dstdout.encoding=UTF-8")
            buildArgs.add("-Dstderr.encoding=UTF-8")
            // Don't ship JVM debug symbols.
            buildArgs.add("-O2")
        }
    }
    // Pull reachability metadata for popular libraries (Jackson, Logback, ...) from
    // the GraalVM Reachability Metadata Repository so we don't have to maintain
    // it by hand.
    metadataRepository {
        enabled.set(true)
    }
}
