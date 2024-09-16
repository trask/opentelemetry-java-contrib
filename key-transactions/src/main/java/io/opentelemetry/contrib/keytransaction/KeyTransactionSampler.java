/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.keytransaction;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;

import static java.util.Collections.emptyList;

public final class KeyTransactionSampler implements Sampler {

  private final Sampler delegate;

  private KeyTransactionSampler(Sampler root) {
    this.delegate = root;
  }

  public static KeyTransactionSampler create(Sampler root) {
    return new KeyTransactionSampler(root);
  }

  @Override
  public SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {

    List<String> existingTransactionNames = getExistingTransactionNames(Span.fromContext(parentContext).getSpanContext().getTraceState());

    List<String> newTransactionNames = getNewTransactionNames(spanKind, attributes);

    SamplingResult result = delegate.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);

    // start time

    return new TransactionSamplingResult(result);
  }

  @Override
  public String getDescription() {
    return String.format("TransactionSampler{root:%s}", delegate.getDescription());
  }

  @Override
  public String toString() {
    return getDescription();
  }

  List<String> getExistingTransactionNames(TraceState traceState) {
    String bt = traceState.get("microsoft.bt");
    // TODO implement me!
    return emptyList();
  }

  private List<String> getNewTransactionNames(SpanKind spanKind, Attributes attributes) {}

  private static class TransactionSamplingResult implements SamplingResult {

    private final SamplingResult delegate;

    private TransactionSamplingResult(SamplingResult delegate) {this.delegate = delegate;}

    @Override
    public SamplingDecision getDecision() {
      return delegate.getDecision();
    }

    @Override
    public Attributes getAttributes() {
      return delegate.getAttributes();
    }

    @Override
    public TraceState getUpdatedTraceState(TraceState parentTraceState) {
      return delegate.getUpdatedTraceState(parentTraceState);
    }
  }
}
