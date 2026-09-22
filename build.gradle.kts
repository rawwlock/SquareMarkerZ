plugins {
    java
}

group = "dev.squaremarkerz"
version = "1.0.1"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Paper 26.3 API. 26.3 is currently an experimental/alpha branch (Minecraft "Wilderness Bound"),
    // so we track its latest build dynamically rather than pinning a build number that will go stale.
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    // squaremap-api 1.4.0 is the first release with Minecraft 26.3 support. compileOnly: never shaded,
    // squaremap provides its own API implementation at runtime.
    compileOnly("xyz.jpenilla:squaremap-api:1.4.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from(sourceSets.main.get().resources.srcDirs) {
            include("plugin.yml")
            expand(props)
        }
    }

    jar {
        archiveBaseName.set("SquareMarkerZ")
    }

    build {
        dependsOn(jar)
    }
}
