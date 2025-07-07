/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.maven;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;

/**
 * Tests for {@link OpenTelemetrySdkService} using junit-pioneer for reliable system property
 * isolation.
 */
public class OpenTelemetrySdkServiceTest {

  /** Verify default config */
  @Test
  @ClearSystemProperty(key = "otel.exporter.otlp.endpoint")
  @ClearSystemProperty(key = "otel.service.name")
  @ClearSystemProperty(key = "otel.resource.attributes")
  public void testDefaultConfiguration() {
    try (OpenTelemetrySdkService openTelemetrySdkService = new OpenTelemetrySdkService()) {

      Resource resource = openTelemetrySdkService.resource;
      assertThat(resource.getAttribute(stringKey("service.name"))).isEqualTo("maven");

      ConfigProperties configProperties = openTelemetrySdkService.getConfigProperties();
      assertThat(configProperties.getString("otel.exporter.otlp.endpoint")).isNull();
      assertThat(configProperties.getString("otel.traces.exporter")).isEqualTo("none");
      assertThat(configProperties.getString("otel.metrics.exporter")).isEqualTo("none");
      assertThat(configProperties.getString("otel.logs.exporter")).isEqualTo("none");
    }
  }

  /** Verify overwritten `service.name`,`key1` and `key2` */
  @Test
  @SetSystemProperty(key = "otel.service.name", value = "my-maven")
  @SetSystemProperty(key = "otel.resource.attributes", value = "key1=val1,key2=val2")
  public void testOverwrittenResourceAttributes() {
    try (OpenTelemetrySdkService openTelemetrySdkService = new OpenTelemetrySdkService()) {

      Resource resource = openTelemetrySdkService.resource;
      assertThat(resource.getAttribute(stringKey("service.name"))).isEqualTo("my-maven");
      assertThat(resource.getAttribute(stringKey("key1"))).isEqualTo("val1");
      assertThat(resource.getAttribute(stringKey("key2"))).isEqualTo("val2");
    }
  }

  /** Verify defining `otel.exporter.otlp.endpoint` works */
  @Test
  @SetSystemProperty(key = "otel.exporter.otlp.endpoint", value = "https://example.com:4317")
  public void testOverwrittenExporterConfiguration_1() {
    try (OpenTelemetrySdkService openTelemetrySdkService = new OpenTelemetrySdkService()) {

      ConfigProperties configProperties = openTelemetrySdkService.getConfigProperties();
      assertThat(configProperties.getString("otel.exporter.otlp.endpoint"))
          .isEqualTo("https://example.com:4317");
      assertThat(configProperties.getString("otel.traces.exporter")).isNull();
      assertThat(configProperties.getString("otel.metrics.exporter")).isNull();
      assertThat(configProperties.getString("otel.logs.exporter")).isNull();
    }
  }

  /** Verify defining `otel.exporter.otlp.traces.endpoint` works */
  @Test
  @ClearSystemProperty(key = "otel.exporter.otlp.endpoint")
  @ClearSystemProperty(key = "otel.traces.exporter")
  @SetSystemProperty(key = "otel.exporter.otlp.traces.endpoint", value = "https://example.com:4317/")
  public void testOverwrittenExporterConfiguration_2() {
    try (OpenTelemetrySdkService openTelemetrySdkService = new OpenTelemetrySdkService()) {

      ConfigProperties configProperties = openTelemetrySdkService.getConfigProperties();
      assertThat(configProperties.getString("otel.exporter.otlp.endpoint")).isNull();
      assertThat(configProperties.getString("otel.exporter.otlp.traces.endpoint"))
          .isEqualTo("https://example.com:4317/");
      assertThat(configProperties.getString("otel.traces.exporter")).isNull();
      assertThat(configProperties.getString("otel.metrics.exporter")).isEqualTo("none");
      assertThat(configProperties.getString("otel.logs.exporter")).isEqualTo("none");
    }
  }

  /** Verify defining `otel.exporter.otlp.traces.endpoint` and `otel.traces.exporter` works */
  @Test
  @ClearSystemProperty(key = "otel.exporter.otlp.endpoint")
  @SetSystemProperty(key = "otel.traces.exporter", value = "otlp")
  @SetSystemProperty(key = "otel.exporter.otlp.traces.endpoint", value = "https://example.com:4317/")
  public void testOverwrittenExporterConfiguration_3() {
    try (OpenTelemetrySdkService openTelemetrySdkService = new OpenTelemetrySdkService()) {

      ConfigProperties configProperties = openTelemetrySdkService.getConfigProperties();
      assertThat(configProperties.getString("otel.exporter.otlp.endpoint")).isNull();
      assertThat(configProperties.getString("otel.exporter.otlp.traces.endpoint"))
          .isEqualTo("https://example.com:4317/");
      assertThat(configProperties.getString("otel.traces.exporter")).isEqualTo("otlp");
      assertThat(configProperties.getString("otel.metrics.exporter")).isEqualTo("none");
      assertThat(configProperties.getString("otel.logs.exporter")).isEqualTo("none");
    }
  }


}
