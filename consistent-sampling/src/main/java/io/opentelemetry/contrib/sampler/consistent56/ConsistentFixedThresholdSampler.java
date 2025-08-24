/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.sampler.consistent56;

import static io.opentelemetry.contrib.sampler.consistent56.ConsistentSamplingUtil.calculateThreshold;

public final class ConsistentFixedThresholdSampler extends ConsistentThresholdSampler {

  private final long threshold;
  private final String description;

  ConsistentFixedThresholdSampler(long threshold) {
    this.threshold = getThreshold(threshold);
    this.description = getThresholdDescription(threshold);
  }

  ConsistentFixedThresholdSampler(double samplingProbability) {
    this(calculateThreshold(samplingProbability));
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public long getThreshold() {
    return threshold;
  }
}
