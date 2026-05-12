// crypto-engine-kotlin (Spike POC v1)
//
// Spring Boot Kotlin gRPC server + EGK (KMP JVM) entegrasyonu.
// EGK fat JAR'ı multi-stage Dockerfile'ın `egk-builder` stage'inden gelir.

import com.google.protobuf.gradle.id

plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.cepsandik"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

// gRPC + Protobuf versiyon sabitleri
val grpcVersion = "1.62.2"
val protobufVersion = "3.25.3"
val grpcKotlinVersion = "1.4.1"

dependencies {
    // ElectionGuard Kotlin Multiplatform — local fat JAR (Docker stage 1 üretir)
    implementation(files("libs/egklib-all.jar"))

    // Spring Boot temel
    implementation("org.springframework.boot:spring-boot-starter")

    // gRPC Spring Boot Starter (yidongnan/devh, Spring Boot 3 uyumlu)
    implementation("net.devh:grpc-server-spring-boot-starter:3.0.0.RELEASE")

    // gRPC core
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")

    // Protobuf Kotlin
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")

    // Kotlin coroutines (gRPC kotlin stub flow API için)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Kotlin runtime
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // EGK transitive bağımlılıkları (fat JAR çoğunu içerir; ekstra runtime ihtiyaçları)
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.18")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc") {}
                id("grpckt") {}
            }
            it.builtins {
                id("kotlin")
            }
        }
    }
}

sourceSets {
    main {
        java {
            srcDirs(
                "build/generated/source/proto/main/java",
                "build/generated/source/proto/main/grpc",
                "build/generated/source/proto/main/grpckt",
                "build/generated/source/proto/main/kotlin",
            )
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
