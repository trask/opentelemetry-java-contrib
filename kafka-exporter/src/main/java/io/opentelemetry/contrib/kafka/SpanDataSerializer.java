/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.kafka;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.exporter.internal.otlp.traces.ResourceSpansMarshaler;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

public final class SpanDataSerializer implements Serializer<Collection<SpanData>> {
  @Override
  public byte[] serialize(String topic, Collection<SpanData> data) {
    requireNonNull(data, "data");
    return convertSpansToRequest(data).toByteArray();
  }

  ExportTraceServiceRequest convertSpansToRequest(Collection<SpanData> spans) {
    List<ResourceSpans> resourceSpansList =
        Arrays.stream(ResourceSpansMarshaler.create(spans))
            .map(
                resourceSpansMarshaler -> {
                  try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    resourceSpansMarshaler.writeBinaryTo(baos);
                    return ResourceSpans.parseFrom(baos.toByteArray());
                  } catch (IOException e) {
                    throw new SerializationException(e);
                  }
                })
            .collect(toList());

    return ExportTraceServiceRequest.newBuilder().addAllResourceSpans(resourceSpansList).build();
  }
}
