plugins {
    `java-library`
}

val serenityVersion: String = "2.0.82"

dependencies {
    api("net.serenity-bdd:serenity-core:${serenityVersion}")
    api("net.serenity-bdd:serenity-spring:${serenityVersion}")
    api("net.serenity-bdd:serenity-cucumber:1.9.45")

    implementation("junit:junit")
    implementation("org.apache.commons:commons-lang3")
    implementation("args4j:args4j:2.33")
}