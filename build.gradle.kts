plugins {
	kotlin("jvm") version "1.9.20"
	id("no.ghpkg") version "0.3.3"
	`maven-publish`
}

group = "no.telenor.kt"
version = versioning.environment()

repositories {
	mavenCentral()
}

dependencies {
	compileOnly("org.slf4j:slf4j-api:2.0.9")
	testImplementation(kotlin("test"))
	testImplementation("ch.qos.logback:logback-classic:1.4.11")
}

tasks.test {
	useJUnitPlatform()
}

kotlin.jvmToolchain(17)

publishing {
	repositories.github.actions()
	publications.register<MavenPublication>("gpr") {
		from(components["kotlin"])
	}
}
