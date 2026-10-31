plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
}

group = "com.millodysingqueue"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(platform("io.ktor:ktor-bom:3.6.0"))
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation("org.liquibase:liquibase-core:5.0.4")
    runtimeOnly("org.postgresql:postgresql:42.7.14")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.ktor:ktor-server-test-host")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

val frontendDirectory = layout.projectDirectory.dir("../frontend")
val frontendWebapp = frontendDirectory.dir("target/webapp")

// The generated frontend is an explicit build dependency, never checked-in source.
val buildFrontend = tasks.register<Exec>("buildFrontend") {
    group = "build"
    description = "Compile the GWT frontend with its pinned Maven wrapper."
    workingDir(frontendDirectory)
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    if (windows) {
        commandLine("cmd", "/c", "mvnw.cmd", "--batch-mode", "--no-transfer-progress", "clean", "verify")
    } else {
        commandLine("./mvnw", "--batch-mode", "--no-transfer-progress", "clean", "verify")
    }
    inputs.dir(frontendDirectory.dir("src"))
    inputs.file(frontendDirectory.file("pom.xml"))
    inputs.dir(frontendDirectory.dir(".mvn"))
    inputs.files(frontendDirectory.file("mvnw"), frontendDirectory.file("mvnw.cmd"))
    outputs.dir(frontendWebapp)
}

tasks.processResources {
    dependsOn(buildFrontend)
    from(frontendWebapp) {
        into("static")
        exclude("WEB-INF/**", "META-INF/**")
    }
}

tasks.test {
    useJUnitPlatform()
}

val integrationTestSourceSet = sourceSets.create("integrationTest")
configurations[integrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get(), configurations.runtimeOnly.get())

dependencies {
    add(integrationTestSourceSet.implementationConfigurationName, sourceSets.main.get().output)
    add(integrationTestSourceSet.implementationConfigurationName, platform("org.testcontainers:testcontainers-bom:2.0.5"))
    add(integrationTestSourceSet.implementationConfigurationName, "org.testcontainers:testcontainers-postgresql")
    add(integrationTestSourceSet.implementationConfigurationName, "org.testcontainers:testcontainers-junit-jupiter")
}

tasks.named<ProcessResources>(integrationTestSourceSet.processResourcesTaskName) {
    from(layout.projectDirectory.dir("../database/init")) {
        into("database-init")
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Test Liquibase and schema constraints against disposable PostgreSQL. Requires Docker."
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    systemProperty("liquibase.analytics.enabled", "false")
    // Database verification must not be skipped just because the source is unchanged.
    outputs.upToDateWhen { false }
}

tasks.check {
    dependsOn(integrationTest)
}

mapOf(
    "dbValidate" to "validate",
    "dbUpdateSql" to "update-sql",
    "dbUpdate" to "update",
    "dbStatus" to "status",
).forEach { (taskName, command) ->
    tasks.register<JavaExec>(taskName) {
        group = "database"
        description = "Run Liquibase $command using MIGRATION_DB_* environment variables."
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.millodysingqueue.database.DatabaseMigrationsKt")
        args(command)
        systemProperty("liquibase.analytics.enabled", "false")
    }
}

tasks.named<Delete>("clean") {
    delete(frontendDirectory.dir("target"))
}

tasks.wrapper {
    gradleVersion = "9.7.0"
    distributionType = Wrapper.DistributionType.BIN
}
