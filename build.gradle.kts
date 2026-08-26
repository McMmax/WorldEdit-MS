plugins {
    kotlin("jvm") version("2.4.0")
    `java-library`
}

group = "dev.terrorify.worldedit"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Minestom core supplied by the consuming app at runtime (like modules/svc).
    compileOnly(project(":"))
    compileOnly(project(":spacetime"))
    compileOnly(project(":polar"))
    compileOnly(project(":solar"))
    // The pure-JVM engine: Clipboard, BoundingBox, ClipboardCapture, paste.
    api(project(":modules:worldedit-core"))
    // Client-capability API — selection visuals via Lunar's WorldEditCUI.
    implementation(project(":modules:whatsmyclient"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

kotlin {
    jvmToolchain(26)
}
