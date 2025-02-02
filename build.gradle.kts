@file:Suppress("VulnerableLibrariesLocal")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "xyz.irodev"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val exposedVersion: String by project
dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.6")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.1.4")
    implementation("net.dv8tion:JDA:5.0.0-beta.9") {
        exclude(module = "opus-java")
    }
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

tasks {
    withType<ShadowJar> {
        dependencies {
            exclude(dependency("org.slf4j:slf4j-api"))
        }
        minimize {
            exclude(dependency("org.jetbrains.exposed:.*:$exposedVersion"))
            exclude(dependency("org.mariadb.jdbc:mariadb-java-client:3.1.4"))
        }
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }
    build {
        dependsOn(shadowJar)
    }
}