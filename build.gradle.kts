plugins {
    java
}

group = "io.mycelar"
version = "0.1.0-SNAPSHOT"

subprojects {
    apply(plugin = "java")

    group = "io.mycelar"
    version = "0.1.0-SNAPSHOT"

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
