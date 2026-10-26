// passki-bom: one version for every PassKi library.
//   implementation(platform("com.alirezaiyan.passki:passki-bom:<version>"))
//   implementation("com.alirezaiyan.passki:passki")   // no version
// The Gradle plugin isn't in it: plugins resolve through pluginManagement, not dependency constraints.
plugins {
    `java-platform`
    alias(libs.plugins.vanniktechPublish)
}

dependencies {
    constraints {
        api(project(":passki"))
        api(project(":passki-compose"))
        api(project(":passki-test"))
        api(project(":passki-demo"))
    }
}

mavenPublishing {
    coordinates(artifactId = "passki-bom")
    publishToMavenCentral()
    if (project.hasProperty("signingInMemoryKey")) signAllPublications()
    pom {
        name.set("PassKi BOM")
        description.set("Bill of materials that keeps every PassKi library on the same version.")
        url.set("https://github.com/rezaiyan/PassKi")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("alirezaiyan")
                name.set("Ali Rezaiyan")
                url.set("https://alirezaiyan.com")
            }
        }
        scm {
            url.set("https://github.com/rezaiyan/PassKi")
            connection.set("scm:git:git://github.com/rezaiyan/PassKi.git")
            developerConnection.set("scm:git:ssh://git@github.com/rezaiyan/PassKi.git")
        }
    }
}
