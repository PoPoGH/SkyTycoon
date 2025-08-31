plugins {
    `java`
    // Pour un setup plus avancé Folia: id("io.papermc.paperweight.userdev") version "1.7.2" (optionnel)
}

group = "fr.popo.skytycoon"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven {
        name = "thenextlvlReleases"
        url = uri("https://repo.thenextlvl.net/releases")
    }
    maven {
        name = "EngineHub"
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "tr7zw-repo"
        url = uri("https://repo.codemc.io/repository/maven-public/")
    }
}

val foliaVersion: String = (findProperty("foliaVersion") as String?) ?: "1.21.8-R0.1-SNAPSHOT"

dependencies {
    // Folia API. Mettre à jour foliaVersion avec la version réellement publiée (ex: 1.21.1-R0.1-SNAPSHOT quand dispo)
    compileOnly("dev.folia:folia-api:$foliaVersion")
    // Fallback Paper (décommentez si Folia version non publiée et que build échoue) :
    // compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // Worlds API pour la gestion des mondes (compileOnly pour éviter de l'inclure dans le JAR)
    compileOnly("net.thenextlvl:worlds:3.6.0")
    // NBT API pour lire les fichiers .schem nativement  
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.15.2")
    // API util libs (optionnel plus tard)
}

tasks.register("printFoliaVersion") {
    doLast { println("Folia API version utilisée: $foliaVersion") }
}

tasks.processResources {
    from("src/main/resources/schematics") {
        into("schematics")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    filesMatching("plugin.yml") {
        expand(
            "name" to project.name,
            "version" to project.version,
        )
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    archiveBaseName.set(project.name)
}
