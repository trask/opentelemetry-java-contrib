/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opamp.client.internal.impl;

import io.opentelemetry.opamp.client.internal.OpampClient;
import io.opentelemetry.opamp.client.internal.request.service.RequestService;
import io.opentelemetry.opamp.client.internal.response.MessageData;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import opamp.proto.AgentDescription;
import opamp.proto.RemoteConfigStatus;

/**
 * Implementation of the OpAMP client that handles communication with the OpAMP server.
 */
public class OpampClientImpl implements OpampClient {

  private final RequestService requestService;
  private final AtomicReference<AgentDescription> agentDescription = new AtomicReference<>();
  private final AtomicReference<RemoteConfigStatus> remoteConfigStatus = new AtomicReference<>();
  @Nullable private volatile Callbacks callbacks;
  private volatile boolean started = false;
  private volatile boolean stopped = false;

  public OpampClientImpl(RequestService requestService) {
    this.requestService = requestService;
  }

  @Override
  public void start(Callbacks callbacks) {
    if (started) {
      throw new IllegalStateException("OpampClient has already been started");
    }
    if (stopped) {
      throw new IllegalStateException("OpampClient has been stopped and cannot be started again");
    }
    
    this.callbacks = callbacks;
    this.started = true;
    
    // Start the request service which will handle the actual network communication
    requestService.start(new RequestServiceCallback(), OpampClientImpl::createRequest);
  }

  @Override
  public void stop() {
    if (!started) {
      throw new IllegalStateException("OpampClient has not been started");
    }
    if (stopped) {
      throw new IllegalStateException("OpampClient has already been stopped");
    }
    
    stopped = true;
    requestService.stop();
    callbacks = null;
  }

  @Override
  public void setAgentDescription(AgentDescription agentDescription) {
    this.agentDescription.set(agentDescription);
    if (started && !stopped) {
      requestService.sendRequest();
    }
  }

  @Override
  public void setRemoteConfigStatus(RemoteConfigStatus remoteConfigStatus) {
    this.remoteConfigStatus.set(remoteConfigStatus);
    if (started && !stopped) {
      requestService.sendRequest();
    }
  }

  /**
   * Gets the current remote config status.
   * This method is used internally and in tests to verify the status has been set correctly.
   */
  @Nullable
  public RemoteConfigStatus getRemoteConfigStatus() {
    return remoteConfigStatus.get();
  }

  @Nullable
  private static io.opentelemetry.opamp.client.internal.request.Request createRequest() {
    // TODO: Implement request creation logic
    // This would typically create an AgentToServer message with current state
    return null;
  }

  private class RequestServiceCallback implements RequestService.Callback {
    @Override
    public void onConnectionSuccess() {
      Callbacks currentCallbacks = callbacks;
      if (currentCallbacks != null) {
        currentCallbacks.onConnect(OpampClientImpl.this);
      }
    }

    @Override
    public void onConnectionFailed(Throwable throwable) {
      Callbacks currentCallbacks = callbacks;
      if (currentCallbacks != null) {
        currentCallbacks.onConnectFailed(OpampClientImpl.this, throwable);
      }
    }

    @Override
    public void onRequestSuccess(io.opentelemetry.opamp.client.internal.response.Response response) {
      Callbacks currentCallbacks = callbacks;
      if (currentCallbacks != null) {
        // TODO: Convert response to MessageData
        MessageData messageData = MessageData.builder().build();
        currentCallbacks.onMessage(OpampClientImpl.this, messageData);
      }
    }

    @Override
    public void onRequestFailed(Throwable throwable) {
      Callbacks currentCallbacks = callbacks;
      if (currentCallbacks != null) {
        currentCallbacks.onConnectFailed(OpampClientImpl.this, throwable);
      }
    }
  }
}