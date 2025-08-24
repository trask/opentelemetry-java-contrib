plugins {
  id("otel.java-conventions")

  id("otel.publish-conventions")
}

description = "OpenTelemetry Baggage Span Processor"
otelJava.moduleName.set("io.opentelemetry.contrib.baggage.processor")

dependencies {
  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-sdk")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")

  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("com.google.auto.service:auto-service")
  testImplementation("org.mockito:mockito-inline")
  testImplementation("com.google.guava:guava")
  testImplementation("org.awaitility:awaitility")
}
