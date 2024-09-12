plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "Key Transactions"
otelJava.moduleName.set("io.opentelemetry.contrib.keytransactions")

dependencies {
  api("io.opentelemetry:opentelemetry-sdk")
}
