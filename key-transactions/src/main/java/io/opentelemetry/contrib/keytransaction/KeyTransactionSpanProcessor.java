package io.opentelemetry.contrib.keytransaction;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;


public class KeyTransactionSpanProcessor implements SpanProcessor {

  @Override
  public void onStart(Context context, ReadWriteSpan readWriteSpan) {

  }

  @Override
  public boolean isStartRequired() {
    return false;
  }

  @Override
  public void onEnd(ReadableSpan readableSpan) {

  }

  @Override
  public boolean isEndRequired() {
    return false;
  }
}
