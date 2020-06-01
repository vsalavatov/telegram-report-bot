plugins {
    kotlin("jvm") version "1.3.72"
    application
}

group = "telegram.bots.reportbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
}

application {
    mainClassName = "telegram.bots.reportbot.EntryPointKt"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot", "telegram", "5.0.0")

    val exposedVersion = "0.25.1"
    implementation("org.jetbrains.exposed", "exposed-core", exposedVersion)
    implementation("org.jetbrains.exposed", "exposed-dao", exposedVersion)
    implementation("org.jetbrains.exposed", "exposed-jdbc", exposedVersion)

    implementation("com.h2database", "h2", "1.4.200")

    implementation("org.slf4j", "slf4j-api", "1.7.30")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}