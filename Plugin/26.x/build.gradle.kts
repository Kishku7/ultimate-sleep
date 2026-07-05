// Ultimate Sleep plugin cell -- MC 26.x (Paper / Spigot / Folia). STANDALONE build, mirroring the
// ChunkSmith plugin cells: consumes the shared plugin source from ../shared_plugin (the plugin is
// a self-contained Bukkit-native reimplementation -- unlike ChunkSmith there is no MC-agnostic
// shared_common to include). Ships ONE jar:
// ultimate-sleep-<ver>+26.x-plugin.jar. Java 25 toolchain, --release 21 bytecode.
//
// folia-api 26.1.2 publishes Gradle module metadata demanding JVM 25, but --release 21 tags the
// resolvable classpaths with TargetJvmVersion 21, which would REJECT the dep. The API is
// compileOnly (never shaded), so the emitted bytecode still respects --release 21 (major 65,
// loads on Java 21+); we just raise the consumer TargetJvmVersion attribute back to 25 on the
// compile/runtime classpaths so the Java-25 API jar resolves.
plugins {
    id("java-library")
}

group = project.property("group") as String
version = project.property("version") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // folia-api is the superset (Bukkit < Spigot < Paper < Folia): the whole shared plugin source
    // compiles against this one classpath; the runtime Platform facade picks the flavour.
    compileOnly("dev.folia:folia-api:26.1.2.build.8-stable")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// Admit the Java-25 folia-api jar despite --release 21 (see header note).
configurations.matching {
    it.name.lowercase().endsWith("compileclasspath") || it.name.lowercase().endsWith("runtimeclasspath")
}.configureEach {
    attributes {
        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

sourceSets {
    main {
        java {
            srcDir("../shared_plugin/bukkit/src/main/java")
            srcDir("../shared_plugin/platform/src/main/java")
        }
        resources {
            srcDir("../shared_plugin/bukkit/src/main/resources")
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-Xlint:all")
    }
    processResources {
        filesMatching("plugin.yml") {
            expand(
                "name" to project.property("artifactName")!!,
                "version" to project.version,
                "group" to project.group,
                "author" to project.property("author")!!,
                "description" to project.property("description")!!,
                "apiVersion" to "26.1",
            )
        }
    }
    jar {
        archiveFileName.set("ultimate-sleep-${project.version}+26.x-plugin.jar")
        manifest {
            attributes("paperweight-mappings-namespace" to "mojang")
        }
    }
}
