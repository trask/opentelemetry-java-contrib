/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.sampler.consistent;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSamplerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;

@AutoService(ConfigurableSamplerProvider.class)
public final class ParentBasedConsistentProbabilitySamplerProvider
    implements ConfigurableSamplerProvider {

  @Override
  public Sampler createSampler(ConfigProperties config) {
    double samplingProbability = config.getDouble("otel.traces.sampler.arg", 1.0d);
    return ConsistentSampler.parentBased(ConsistentSampler.probabilityBased(samplingProbability));
  }

  @Override
  public String getName() {
    return "parentbased_consistent_probability";
  }
}
