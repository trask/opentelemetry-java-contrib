/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.jmxmetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

class GroovyRunnerTest {

  @Test
  @SetSystemProperty(
      key = "otel.jmx.service.url",
      value = "service:jmx:rmi:///jndi/rmi://localhost:12345/jmxrmi")
  @SetSystemProperty(key = "otel.jmx.target.system", value = "jvm")
  void loadTargetScript() throws Exception {
    JmxConfig config = new JmxConfig();

    assertThatCode(config::validate).doesNotThrowAnyException();

    JmxClient stub =
        new JmxClient(config) {
          @Override
          public List<ObjectName> query(ObjectName objectName) {
            return emptyList();
          }
        };

    GroovyRunner runner = new GroovyRunner(config, stub, new GroovyMetricEnvironment(config));

    assertThat(runner.getScripts()).hasSize(1);
    runner.run();
  }

  @Test
  @SetSystemProperty(key = "otel.jmx.service.url", value = "requiredValue")
  @SetSystemProperty(key = "otel.jmx.target.system", value = "notAProvidededTargetSystem")
  void loadUnavailableTargetScript() {
    JmxConfig config = new JmxConfig();

    assertThatThrownBy(() -> new GroovyRunner(config, null, null))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage("Failed to load target-systems/notaprovidededtargetsystem.groovy");
  }

  @Test
  @SetSystemProperty(
      key = "otel.jmx.service.url",
      value = "service:jmx:rmi:///jndi/rmi://localhost:12345/jmxrmi")
  @SetSystemProperty(key = "otel.jmx.target.system", value = "jvm,hadoop")
  @SetSystemProperty(
      key = "otel.jmx.groovy.script",
      value = "src/integrationTest/resources/script.groovy")
  void loadScriptAndTargetSystems() throws Exception {
    JmxConfig config = new JmxConfig();

    assertThatCode(config::validate).doesNotThrowAnyException();

    JmxClient stub =
        new JmxClient(config) {
          @Override
          public List<ObjectName> query(ObjectName objectName) {
            return emptyList();
          }
        };

    GroovyRunner runner = new GroovyRunner(config, stub, new GroovyMetricEnvironment(config));

    assertThat(runner.getScripts()).hasSize(3);
    runner.run();
  }

  @Test
  @SetSystemProperty(
      key = "otel.jmx.service.url",
      value = "service:jmx:rmi:///jndi/rmi://localhost:12345/jmxrmi")
  @SetSystemProperty(key = "otel.jmx.target.system", value = "jvm")
  @SetSystemProperty(key = "otel.jmx.groovy.script", value = "")
  void loadScriptAndTargetSystemWithBlankInputForScript() throws Exception {
    JmxConfig config = new JmxConfig();

    assertThatCode(config::validate).doesNotThrowAnyException();

    JmxClient stub =
        new JmxClient(config) {
          @Override
          public List<ObjectName> query(ObjectName objectName) {
            return emptyList();
          }
        };

    GroovyRunner runner = new GroovyRunner(config, stub, new GroovyMetricEnvironment(config));

    assertThat(runner.getScripts()).hasSize(1);
    runner.run();
  }
}
