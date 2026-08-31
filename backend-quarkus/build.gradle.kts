import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.allopen")
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val nimbusJoseJwtVersion: String by project
val sqliteJdbcVersion: String by project
val kotlinVersion: String by project

// The Quarkus BOM's enforcedPlatform otherwise wins version conflicts for
// the Kotlin libraries too, which can pull in artifacts newer than this
// project's Kotlin compiler plugin understands. Pin the whole group to match.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(kotlinVersion)
        }
    }
}

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-mailer")

    // Generic OIDC (authorization code + PKCE) and ID token verification,
    // provider-agnostic per ADR 0004. See docs/decisions/0002-manual-oidc-client.md.
    implementation("com.nimbusds:nimbus-jose-jwt:${nimbusJoseJwtVersion}")

    // Plain JDBC over SQLite, no ORM. See docs/decisions/0001-plain-jdbc-and-sqlite.md.
    implementation("org.xerial:sqlite-jdbc:${sqliteJdbcVersion}")

    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

group = "app.nanotes"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Quarkus's CDI container (ArC) generates subclass proxies for normal-scoped
// beans (@ApplicationScoped/@RequestScoped) — that requires the bean class
// (and the members ArC proxies) to be non-final, which every Kotlin class is
// by default. This plugin opens exactly the classes/members that need it,
// instead of marking every class `open` by hand.
allOpen {
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.enterprise.context.RequestScoped")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        javaParameters = true
        // Opts into Kotlin's upcoming default: a constructor-parameter
        // annotation with no explicit use-site target (e.g. @ConfigProperty
        // in AppConfig) applies to both the parameter and the backing
        // field/property, not just the parameter. Silences a forward-compat
        // warning now instead of after the next Kotlin upgrade.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
