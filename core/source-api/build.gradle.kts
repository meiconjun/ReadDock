plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/meiconjun/ReadDock")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").getOrElse("github-actions")
                password = providers.environmentVariable("GITHUB_TOKEN").getOrElse("")
            }
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
