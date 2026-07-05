// Ultimate Sleep plugin cell -- MC 1.20.x (Paper / Spigot / Folia). STANDALONE build, mirroring
// the ChunkSmith plugin cells: consumes the shared plugin source from ../shared_plugin (the
// plugin is a self-contained Bukkit-native reimplementation -- unlike ChunkSmith there is no
// MC-agnostic shared_common to include). Ships ONE jar:
// ultimate-sleep-<ver>+1.20.x-plugin.jar. api-version 1.20.
//
// Java note: compile on JDK 21 (--release 17 output). folia-api 1.20.6 is published as a Java-21
// artifact (MC 1.20.5+ requires Java 21), so its module metadata demands JVM 21 and its class
// files need JDK 21 to be read. We keep --release 17 so the emitted plugin bytecode (major 61)
// still loads on the Java-17 servers of the 1.20.1-1.20.4 sub-line; the API is compileOnly
// (never shaded), and the consumer TargetJvmVersion attribute is raised to 21 so the Java-21
// API jar resolves.
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
    compileOnly("dev.folia:folia-api:1.20.6-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Admit the Java-21 folia-api jar despite --release 17 (see header note).
configurations.matching {
    it.name.lowercase().endsWith("compileclasspath") || it.name.lowercase().endsWith("runtimeclasspath")
}.configureEach {
    attributes {
        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
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
        options.release.set(17)
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
                "apiVersion" to "1.20",
            )
        }
    }
    jar {
        archiveFileName.set("ultimate-sleep-${project.version}+1.20.x-plugin.jar")
        manifest {
            attributes("paperweight-mappings-namespace" to "mojang")
        }
    }
}
