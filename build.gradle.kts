plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)

    // Meteor
    modCompileOnly(libs.meteor.client) { isTransitive = false }
    compileOnly("meteordevelopment:orbit:0.2.4")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

fun toMinecraftCompat(version: String): String {
    val match = Regex("""^(\d{2})\.([1-9]\d*)(?:\.([1-9]\d*))?$""")
        .matchEntire(version)

    if (match != null) {
        val (year, drop, _) = match.destructured
        return "~$year.$drop"
    }

    val oldMatch = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?$""")
        .matchEntire(version)
    if (oldMatch != null) {
        val (major, minor, _) = oldMatch.destructured
        return "~$major.$minor"
    }

    error("Invalid Minecraft version format: $version. Expected YY.D.H or M.m.p")
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "minecraft_version" to toMinecraftCompat(libs.versions.minecraft.get()),
            "jdk_version" to libs.versions.jdk.get(),
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }
}
