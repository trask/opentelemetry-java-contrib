/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.jmxmetrics;

import com.google.auto.value.AutoValue;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.InstrumentValueType;

@AutoValue
abstract final class InstrumentDescriptor {

  static InstrumentDescriptor create(
      String name,
      String description,
      String unit,
      InstrumentType instrumentType,
      InstrumentValueType valueType) {
    return new AutoValue_InstrumentDescriptor(name, description, unit, instrumentType, valueType);
  }

  abstract String getName();

  abstract String getDescription();

  abstract String getUnit();

  abstract InstrumentType getInstrumentType();

  abstract InstrumentValueType getValueType();
}
