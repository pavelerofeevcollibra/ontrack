import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    groovy
    `java-library`
}

apply(plugin = "org.springframework.boot")
apply(plugin = "com.bmuschko.docker-remote-api")

val seleniumVersion = "3.11.0"

dependencies {
    implementation("org.codehaus.groovy:groovy")

    testImplementation(project(":ontrack-client"))
    testImplementation(project(":ontrack-dsl"))
    testImplementation(project(":ontrack-dsl-shell"))
    testImplementation(project(":ontrack-test-utils"))
    testImplementation("org.apache.commons:commons-lang3")
    testImplementation("commons-io:commons-io")
    testImplementation("org.codehaus.groovy:groovy-xml")
    testImplementation("org.springframework.boot:spring-boot-starter")

    testImplementation("org.influxdb:influxdb-java")

    testImplementation("org.seleniumhq.selenium:selenium-java:$seleniumVersion")
    testImplementation("org.seleniumhq.selenium:selenium-support:$seleniumVersion")
}

//noinspection GroovyAssignabilityCheck
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.seleniumhq.selenium" && requested.name != "htmlunit-driver") {
            useVersion(seleniumVersion)
        }
    }
}

/**
 * Packaging
 */

val bootJar = tasks.getByName<BootJar>("bootJar") {
    bootInf {
        from(sourceSets["test"].output)
        into("classes")
    }
    classpath(configurations.named("testRuntimeClasspath"))
    mainClassName = "net.nemerosa.ontrack.acceptance.boot.Start"
}

val normaliseJar by tasks.registering(Copy::class) {
    dependsOn(bootJar)
    from("$buildDir/libs/")
    include("ontrack-acceptance-$version.jar")
    into("$buildDir/libs/")
    rename("ontrack-acceptance-$version.jar", "ontrack-acceptance.jar")
}

val acceptanceDockerPrepareEnv by tasks.registering(Copy::class) {
    dependsOn(normaliseJar)
    from("${buildDir}/libs/ontrack-acceptance.jar")
    into("${projectDir}/src/main/docker")
}

tasks.named("assemble") {
    dependsOn(normaliseJar)
}

val dockerBuild by tasks.registering(DockerBuildImage::class) {
    dependsOn(acceptanceDockerPrepareEnv)
    inputDir.set(file("src/main/docker"))
    tags.add("nemerosa/ontrack-acceptance:$version")
    tags.add("nemerosa/ontrack-acceptance:latest")
}

/**
 * Local test definitions
 */

val ontrackUrl: String by project
val ontrackJvmOptions: String by project
val ontrackImplicitWait: String by project

val acceptanceTest by tasks.registering(Test::class) {
    outputs.upToDateWhen { false }  // Always run tests
    include("**/ACC*.class")
    ignoreFailures = true
    systemProperties(
            mapOf(
                    "ontrack.url" to ontrackUrl,
                    "ontrack.implicitWait" to ontrackImplicitWait
            )
    )
}

// Disable unit tests (none in this project)

tasks.named<Test>("test") {
    enabled = false
}

// Javadoc and sources for the tests

val publishedTasks = mutableListOf<Jar>(bootJar)

if (hasProperty("documentation")) {

    val groovydoc = tasks.named<Groovydoc>("groovydoc") {
        source = project.the<SourceSetContainer>()["test"].allSource
    }

    val javadoc = tasks.named<Javadoc>("javadoc") {
        source = project.the<SourceSetContainer>()["test"].allJava
    }

    val javadocJar = tasks.getByName<Jar>("javadocJar") {
        from(javadoc)
        from(groovydoc)
    }

    val sourcesJar = tasks.getByName<Jar>("sourcesJar") {
        from(project.the<SourceSetContainer>()["test"].allSource)
    }

    publishedTasks.add(javadocJar)
    publishedTasks.add(sourcesJar)

}

// Publications

configure<PublishingExtension> {
    publications {
        named<MavenPublication>("mavenCustom") {
            setArtifacts(publishedTasks)
        }
    }
}
