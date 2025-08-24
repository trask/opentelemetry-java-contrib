plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "Tools to intercept and process signals globally."
otelJava.moduleName.set("io.opentelemetry.contrib.processors")

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

// AutoService plugin is applied by otel.java-conventions, but we need to declare the dependency
// to use @AutoService annotation

dependencies {
  annotationProcessor("com.google.auto.service:auto-service")

  api("io.opentelemetry:opentelemetry-sdk")
  compileOnly("com.google.auto.service:auto-service")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  compileOnly("io.opentelemetry:opentelemetry-api-incubator")

  // For EventToSpanEventBridge
  implementation("io.opentelemetry:opentelemetry-exporter-otlp-common")
  implementation("com.fasterxml.jackson.core:jackson-core")

  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-incubator")
  testImplementation("io.opentelemetry:opentelemetry-exporter-logging")
}
