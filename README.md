# MDC Transaction

A simple utility class for Kotlin and Java that can be used to modify
the MDC, and cleanup for you when you're done.

Note that this requires Java 17+

## Dependency

In your gradle file

_Follow [this guide](https://github.com/testersen/no.ghpkg) on how to set up your environment for GitHub packages._

<!-- @formatter:off -->
```kt
plugins {
  id("no.ghpkg") version "0.3.3"
}

repositories {
  git.hub("telenornorway", "mdctransaction.kt")
  // or <.. the below> if you're spicy 🌶️
  // git.hub("telenornorway")
}

dependencies {
  implementation("no.telenor.kt:mdc-transaction:<VERSION HERE>")
  // If you're not using a framework like Spring Boot,
  // be sure to also include SLF4J and Logback.
  implementation("org.slf4j:slf4j-api:2.0.9")
  runtimeOnly("ch.qos.logback:logback-classic:1.4.11")
}
```
<!-- @formatter:on -->

## Usage

<!-- @formatter:off -->
```kt
import no.telenor.kt.MDCTransaction

val snapshot = MDCTransaction
  .put("foo", "a")
  .put("bar", null)
  .put("baz", "a", "b")
  .put("qux", null, "b")
  .putIfNotNull("quux", "a")
  .putIfNotNull("waldo", null)
  .commit()

// do stuff

snapshot.restore()
```
<!-- @formatter:on -->
