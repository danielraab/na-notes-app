import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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

val quarkusPlatformGroupId = providers.gradleProperty("quarkusPlatformGroupId").get()
val quarkusPlatformArtifactId = providers.gradleProperty("quarkusPlatformArtifactId").get()
val quarkusPlatformVersion = providers.gradleProperty("quarkusPlatformVersion").get()
val nimbusJoseJwtVersion = providers.gradleProperty("nimbusJoseJwtVersion").get()
val sqliteJdbcVersion = providers.gradleProperty("sqliteJdbcVersion").get()
val kotlinVersion = providers.gradleProperty("kotlinVersion").get()

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
        // Match `java { targetCompatibility }` above. Without this, the Kotlin
        // plugin infers its target from the JDK running Gradle (Java 25 on some
        // dev machines, which Kotlin 2.2 caps at 24), and Gradle 9's Java/Kotlin
        // target-consistency check then fails the build. Docker images build on
        // JDK 21, so 21 is the real floor regardless.
        jvmTarget = JvmTarget.JVM_21
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
