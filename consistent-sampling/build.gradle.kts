plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "Sampler and exporter implementations for consistent sampling"
otelJava.moduleName.set("io.opentelemetry.contrib.sampler")

dependencies {
  api("io.opentelemetry:opentelemetry-sdk-trace")
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testImplementation("org.hipparchus:hipparchus-core:4.0.3")
  testImplementation("org.hipparchus:hipparchus-stat:4.0.3")
}

tasks {
  withType<Test>().configureEach {
    develocity.testRetry {
      // TODO (trask) fix flaky tests and remove this workaround
      // -PdiagProfiling disables retries to get clean single-run timing
      if (System.getenv().containsKey("CI") && !project.hasProperty("diagProfiling")) {
        maxRetries.set(5)
      }
    }

    // TEMPORARY diagnostic instrumentation for slow Windows/Java 26 tests.
    // Enable with -PdiagProfiling. Remove before merging.
    // Uses relative paths (working dir = project dir) to stay Windows-safe
    // (absolute Windows paths contain a ':' which breaks -Xlog parsing).
    if (project.hasProperty("diagProfiling")) {
      val diagDir = layout.buildDirectory.dir("diag").get().asFile
      doFirst {
        diagDir.mkdirs()
      }
      jvmArgs(
        "-XX:StartFlightRecording=filename=build/diag/consistent-sampling.jfr," +
          "settings=profile,dumponexit=true,maxsize=250m",
        "-Xlog:gc*:file=build/diag/gc.log",
      )
    }
  }
}
