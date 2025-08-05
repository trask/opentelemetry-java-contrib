/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opamp.client.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.opentelemetry.opamp.client.internal.OpampClient;
import io.opentelemetry.opamp.client.internal.connectivity.http.OkHttpSender;
import io.opentelemetry.opamp.client.internal.request.Request;
import io.opentelemetry.opamp.client.internal.request.service.HttpRequestService;
import io.opentelemetry.opamp.client.internal.request.service.RequestService;
import io.opentelemetry.opamp.client.internal.response.MessageData;
import io.opentelemetry.opamp.client.internal.state.State;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.StartStop;
import okio.Buffer;
import okio.ByteString;
import opamp.proto.AgentConfigFile;
import opamp.proto.AgentConfigMap;
import opamp.proto.AgentDescription;
import opamp.proto.AgentIdentification;
import opamp.proto.AgentRemoteConfig;
import opamp.proto.AgentToServer;
import opamp.proto.AgentToServerFlags;
import opamp.proto.AnyValue;
import opamp.proto.EffectiveConfig;
import opamp.proto.KeyValue;
import opamp.proto.RemoteConfigStatus;
import opamp.proto.RemoteConfigStatuses;
import opamp.proto.ServerErrorResponse;
import opamp.proto.ServerToAgent;
import opamp.proto.ServerToAgentFlags;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"SystemOut", "CatchingUnchecked", "InterruptedExceptionSwallowed"})
class OpampClientImplTest {
  private RequestService requestService;
  private OpampClientState state;
  private OpampClientImpl client;
  private TestEffectiveConfig effectiveConfig;
  private TestCallbacks callbacks;
  @StartStop private final MockWebServer server = new MockWebServer();

  @BeforeEach
  void setUp() {
    System.out.println("[DEBUG] Test setUp - Thread: " + Thread.currentThread().getName() + 
                       ", Time: " + System.currentTimeMillis());
    effectiveConfig =
        new TestEffectiveConfig(
            new EffectiveConfig.Builder()
                .config_map(createAgentConfigMap("first", "first content"))
                .build());
    state =
        new OpampClientState(
            new State.RemoteConfigStatus(
                getRemoteConfigStatus(RemoteConfigStatuses.RemoteConfigStatuses_UNSET)),
            new State.SequenceNum(1L),
            new State.AgentDescription(new AgentDescription.Builder().build()),
            new State.Capabilities(5L),
            new State.InstanceUid(new byte[] {1, 2, 3}),
            new State.Flags((long) AgentToServerFlags.AgentToServerFlags_Unspecified.getValue()),
            effectiveConfig);
    requestService = createHttpService();
    System.out.println("[DEBUG] Test setUp complete - Server URL: " + server.url("/v1/opamp"));
  }

  @AfterEach
  void tearDown() {
    System.out.println("[DEBUG] Test tearDown - Thread: " + Thread.currentThread().getName() + 
                       ", Time: " + System.currentTimeMillis());
    if (client != null) {
      try {
        client.stop();
        System.out.println("[DEBUG] Client stopped successfully");
      } catch (Exception e) {
        System.out.println("[DEBUG] Error stopping client: " + e.getMessage());
        e.printStackTrace();
      }
    }
    
    // Clear any remaining server requests
    try {
      while (server.takeRequest(100, TimeUnit.MILLISECONDS) != null) {
        System.out.println("[DEBUG] Cleared remaining request from server queue");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    
    System.out.println("[DEBUG] Test tearDown complete");
  }

  @Test
  void verifyFieldsSent() {
    // Check first request
    ServerToAgent response = new ServerToAgent.Builder().build();
    RecordedRequest firstRequest = initializeClient(response);
    AgentToServer firstMessage = getAgentToServerMessage(firstRequest);

    // Required first request fields
    assertThat(firstMessage.instance_uid).isNotNull();
    assertThat(firstMessage.sequence_num).isEqualTo(1);
    assertThat(firstMessage.capabilities).isEqualTo(state.capabilities.get());
    assertThat(firstMessage.agent_description).isEqualTo(state.agentDescription.get());
    assertThat(firstMessage.effective_config).isEqualTo(state.effectiveConfig.get());
    assertThat(firstMessage.remote_config_status).isEqualTo(state.remoteConfigStatus.get());

    // Check second request
    enqueueServerToAgentResponse(response);
    RemoteConfigStatus remoteConfigStatus =
        new RemoteConfigStatus.Builder()
            .status(RemoteConfigStatuses.RemoteConfigStatuses_APPLYING)
            .build();
    client.setRemoteConfigStatus(remoteConfigStatus);

    RecordedRequest secondRequest = takeRequest();
    AgentToServer secondMessage = getAgentToServerMessage(secondRequest);

    // Verify only changed and required fields are present
    assertThat(secondMessage.instance_uid).isNotNull();
    assertThat(secondMessage.sequence_num).isEqualTo(2);
    assertThat(firstMessage.capabilities).isEqualTo(state.capabilities.get());
    assertThat(secondMessage.agent_description).isNull();
    assertThat(secondMessage.effective_config).isNull();
    assertThat(secondMessage.remote_config_status).isEqualTo(remoteConfigStatus);

    // Check state observing
    enqueueServerToAgentResponse(response);
    EffectiveConfig otherConfig =
        new EffectiveConfig.Builder()
            .config_map(createAgentConfigMap("other", "other value"))
            .build();
    effectiveConfig.config = otherConfig;
    effectiveConfig.notifyUpdate();

    // Check third request
    RecordedRequest thirdRequest = takeRequest();
    AgentToServer thirdMessage = getAgentToServerMessage(thirdRequest);

    assertThat(thirdMessage.instance_uid).isNotNull();
    assertThat(thirdMessage.sequence_num).isEqualTo(3);
    assertThat(firstMessage.capabilities).isEqualTo(state.capabilities.get());
    assertThat(thirdMessage.agent_description).isNull();
    assertThat(thirdMessage.remote_config_status).isNull();
    assertThat(thirdMessage.effective_config)
        .isEqualTo(otherConfig); // it was changed via observable state

    // Check when the server requests for all fields

    ServerToAgent reportFullState =
        new ServerToAgent.Builder()
            .flags(ServerToAgentFlags.ServerToAgentFlags_ReportFullState.getValue())
            .build();
    enqueueServerToAgentResponse(reportFullState);
    requestService.sendRequest();
    takeRequest(); // Notifying the client to send all fields next time

    // Request with all fields
    enqueueServerToAgentResponse(new ServerToAgent.Builder().build());
    requestService.sendRequest();

    AgentToServer fullRequestedMessage = getAgentToServerMessage(takeRequest());

    // Required first request fields
    assertThat(fullRequestedMessage.instance_uid).isNotNull();
    assertThat(fullRequestedMessage.sequence_num).isEqualTo(5);
    assertThat(fullRequestedMessage.capabilities).isEqualTo(state.capabilities.get());
    assertThat(fullRequestedMessage.agent_description).isEqualTo(state.agentDescription.get());
    assertThat(fullRequestedMessage.effective_config).isEqualTo(state.effectiveConfig.get());
    assertThat(fullRequestedMessage.remote_config_status).isEqualTo(state.remoteConfigStatus.get());
  }

  @Test
  void verifyStop() {
    initializeClient();

    enqueueServerToAgentResponse(new ServerToAgent.Builder().build());
    client.stop();

    AgentToServer agentToServerMessage = getAgentToServerMessage(takeRequest());
    assertThat(agentToServerMessage.agent_disconnect).isNotNull();
  }

  @Test
  void verifyStartOnlyOnce() {
    initializeClient();
    try {
      client.start(callbacks);
      fail("Should have thrown an exception");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("The client has already been started");
    }
  }

  @Test
  void onSuccess_withChangesToReport_notifyCallbackOnMessage() {
    System.out.println("[DEBUG] Starting onSuccess_withChangesToReport_notifyCallbackOnMessage test");
    initializeClient();
    AgentRemoteConfig remoteConfig =
        new AgentRemoteConfig.Builder()
            .config(createAgentConfigMap("someKey", "someValue"))
            .build();
    ServerToAgent serverToAgent = new ServerToAgent.Builder().remote_config(remoteConfig).build();
    enqueueServerToAgentResponse(serverToAgent);

    System.out.println("[DEBUG] Before sendRequest - onMessageCalls: " + callbacks.onMessageCalls.get());
    // Force request
    requestService.sendRequest();

    System.out.println("[DEBUG] After sendRequest - Starting await for callback");
    // Await for onMessage call - increased timeout for debugging
    await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100))
           .until(() -> {
             int calls = callbacks.onMessageCalls.get();
             System.out.println("[DEBUG] Current onMessageCalls: " + calls + 
                              ", Thread: " + Thread.currentThread().getName() + 
                              ", Time: " + System.currentTimeMillis());
             return calls == 1;
           });

    System.out.println("[DEBUG] Await completed - verifying callback");
    verify(callbacks).onMessage(MessageData.builder().setRemoteConfig(remoteConfig).build());
    System.out.println("[DEBUG] Test completed successfully");
  }

  @Test
  void onSuccess_withNoChangesToReport_doNotNotifyCallbackOnMessage() {
    System.out.println("[DEBUG] Starting onSuccess_withNoChangesToReport_doNotNotifyCallbackOnMessage test");
    initializeClient();
    ServerToAgent serverToAgent = new ServerToAgent.Builder().build();
    enqueueServerToAgentResponse(serverToAgent);

    System.out.println("[DEBUG] Before sendRequest - onMessageCalls: " + callbacks.onMessageCalls.get());
    // Force request
    requestService.sendRequest();

    System.out.println("[DEBUG] After sendRequest - Starting await during period");
    // Giving some time for the callback to get called - increased timeout for debugging
    await().during(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100))
           .untilAsserted(() -> {
             int calls = callbacks.onMessageCalls.get();
             System.out.println("[DEBUG] Current onMessageCalls (should stay 0): " + calls + 
                              ", Thread: " + Thread.currentThread().getName() + 
                              ", Time: " + System.currentTimeMillis());
             assertThat(calls).isEqualTo(0);
           });

    System.out.println("[DEBUG] Await completed - verifying no callback was called");
    verify(callbacks, never()).onMessage(any());
    System.out.println("[DEBUG] Test completed successfully");
  }

  @Test
  void verifyAgentDescriptionSetter() {
    initializeClient();
    AgentDescription agentDescription =
        getAgentDescriptionWithOneIdentifyingValue("service.name", "My service");

    // Update when changed
    enqueueServerToAgentResponse(new ServerToAgent.Builder().build());
    client.setAgentDescription(agentDescription);
    assertThat(takeRequest()).isNotNull();

    // Ignore when the provided value is the same as the current one
    enqueueServerToAgentResponse(new ServerToAgent.Builder().build());
    client.setAgentDescription(agentDescription);
    assertThat(takeRequest()).isNull();
  }

  @Test
  void verifyRemoteConfigStatusSetter() {
    initializeClient();
    RemoteConfigStatus remoteConfigStatus =
        getRemoteConfigStatus(RemoteConfigStatuses.RemoteConfigStatuses_APPLYING);

    // Update when changed
    enqueueServerToAgentResponse(new ServerToAgent.Builder().build());
    client.setRemoteConfigStatus(remoteConfigStatus);
    assertThat(takeRequest()).isNotNull();

    // Ignore when the provided value is the same as the current one
    enqueueServerToAgentResponse(new ServerToAgent.Builder().build());
    client.setRemoteConfigStatus(remoteConfigStatus);
    assertThat(takeRequest()).isNull();
  }

  @Test
  void onConnectionSuccessful_notifyCallback() {
    System.out.println("[DEBUG] Starting onConnectionSuccessful_notifyCallback test");
    initializeClient();

    System.out.println("[DEBUG] Client initialized - Starting await for connect callback");
    await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100))
           .until(() -> {
             int calls = callbacks.onConnectCalls.get();
             System.out.println("[DEBUG] Current onConnectCalls: " + calls + 
                              ", Thread: " + Thread.currentThread().getName() + 
                              ", Time: " + System.currentTimeMillis());
             return calls == 1;
           });

    System.out.println("[DEBUG] Await completed - verifying callbacks");
    verify(callbacks).onConnect();
    verify(callbacks, never()).onConnectFailed(any());
    System.out.println("[DEBUG] Test completed successfully");
  }

  @Test
  void onFailedResponse_keepFieldsForNextRequest() {
    initializeClient();

    // Mock failed request
    server.enqueue(new MockResponse.Builder().code(404).build());

    // Adding a non-constant field
    AgentDescription agentDescription =
        getAgentDescriptionWithOneIdentifyingValue("service.namespace", "something");
    client.setAgentDescription(agentDescription);

    // Assert first request contains it
    assertThat(getAgentToServerMessage(takeRequest()).agent_description)
        .isEqualTo(agentDescription);

    // Since it failed, send the agent description field in the next request
    enqueueServerToAgentResponse(new ServerToAgent.Builder().build());
    requestService.sendRequest();
    assertThat(getAgentToServerMessage(takeRequest()).agent_description)
        .isEqualTo(agentDescription);

    // When there's no failure, do not keep it.
    enqueueServerToAgentResponse(new ServerToAgent.Builder().build());
    requestService.sendRequest();
    assertThat(getAgentToServerMessage(takeRequest()).agent_description).isNull();
  }

  @Test
  void onFailedResponse_withServerErrorData_notifyCallback() {
    System.out.println("[DEBUG] Starting onFailedResponse_withServerErrorData_notifyCallback test");
    initializeClient();

    ServerErrorResponse errorResponse = new ServerErrorResponse.Builder().build();
    enqueueServerToAgentResponse(new ServerToAgent.Builder().error_response(errorResponse).build());

    System.out.println("[DEBUG] Before sendRequest - onErrorResponseCalls: " + callbacks.onErrorResponseCalls.get());
    // Force request
    requestService.sendRequest();

    System.out.println("[DEBUG] After sendRequest - Starting await for error callback");
    await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100))
           .until(() -> {
             int calls = callbacks.onErrorResponseCalls.get();
             System.out.println("[DEBUG] Current onErrorResponseCalls: " + calls + 
                              ", Thread: " + Thread.currentThread().getName() + 
                              ", Time: " + System.currentTimeMillis());
             return calls == 1;
           });

    System.out.println("[DEBUG] Await completed - verifying callbacks");
    verify(callbacks).onErrorResponse(errorResponse);
    verify(callbacks, never()).onMessage(any());
    System.out.println("[DEBUG] Test completed successfully");
  }

  @Test
  void onConnectionFailed_notifyCallback() {
    initializeClient();
    Throwable throwable = new Throwable();

    client.onConnectionFailed(throwable);

    verify(callbacks).onConnectFailed(throwable);
  }

  @Test
  void whenServerProvidesNewInstanceUid_useIt() {
    System.out.println("[DEBUG] Starting whenServerProvidesNewInstanceUid_useIt test");
    initializeClient();
    byte[] initialUid = state.instanceUid.get();
    System.out.println("[DEBUG] Initial UID: " + java.util.Arrays.toString(initialUid));

    byte[] serverProvidedUid = new byte[] {1, 2, 3};
    ServerToAgent response =
        new ServerToAgent.Builder()
            .agent_identification(
                new AgentIdentification.Builder()
                    .new_instance_uid(ByteString.of(serverProvidedUid))
                    .build())
            .build();

    enqueueServerToAgentResponse(response);
    System.out.println("[DEBUG] Before sendRequest - current UID: " + 
                       java.util.Arrays.toString(state.instanceUid.get()));
    requestService.sendRequest();

    System.out.println("[DEBUG] After sendRequest - Starting await for UID change");
    await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100))
           .until(() -> {
             byte[] currentUid = state.instanceUid.get();
             boolean changed = currentUid != initialUid;
             System.out.println("[DEBUG] Current UID: " + java.util.Arrays.toString(currentUid) + 
                              ", Changed: " + changed + 
                              ", Thread: " + Thread.currentThread().getName() + 
                              ", Time: " + System.currentTimeMillis());
             return changed;
           });

    System.out.println("[DEBUG] Await completed - verifying UID change");
    assertThat(state.instanceUid.get()).isEqualTo(serverProvidedUid);
    System.out.println("[DEBUG] Test completed successfully - Final UID: " + 
                       java.util.Arrays.toString(state.instanceUid.get()));
  }

  @Test
  void flakiness_stress_test_all_timing_operations() {
    System.out.println("[DEBUG] Starting stress test for flakiness detection");
    for (int i = 1; i <= 10; i++) {
      System.out.println("[DEBUG] ===== ITERATION " + i + " =====");
      
      try {
        // Test connection callback timing
        initializeClient();
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(50))
               .until(() -> callbacks.onConnectCalls.get() == 1);
        
        // Test message callback timing
        AgentRemoteConfig remoteConfig =
            new AgentRemoteConfig.Builder()
                .config(createAgentConfigMap("key" + i, "value" + i))
                .build();
        ServerToAgent serverToAgent = new ServerToAgent.Builder().remote_config(remoteConfig).build();
        enqueueServerToAgentResponse(serverToAgent);
        
        int beforeCalls = callbacks.onMessageCalls.get();
        requestService.sendRequest();
        
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(50))
               .until(() -> callbacks.onMessageCalls.get() > beforeCalls);
        
        // Test instance UID update timing  
        byte[] newUid = new byte[] {(byte)i, (byte)(i+1), (byte)(i+2)};
        ServerToAgent uidResponse =
            new ServerToAgent.Builder()
                .agent_identification(
                    new AgentIdentification.Builder()
                        .new_instance_uid(ByteString.of(newUid))
                        .build())
                .build();
        
        enqueueServerToAgentResponse(uidResponse);
        byte[] beforeUid = state.instanceUid.get();
        requestService.sendRequest();
        
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(50))
               .until(() -> !java.util.Arrays.equals(state.instanceUid.get(), beforeUid));
        
        System.out.println("[DEBUG] Iteration " + i + " completed successfully");
        
        // Force cleanup between iterations
        client.stop();
        Thread.sleep(100); // Small delay to ensure cleanup
        
      } catch (Exception e) {
        System.out.println("[ERROR] Iteration " + i + " failed: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException("Stress test failed at iteration " + i, e);
      }
    }
    
    System.out.println("[DEBUG] All stress test iterations completed successfully");
  }

  private static AgentToServer getAgentToServerMessage(RecordedRequest request) {
    try {
      return AgentToServer.ADAPTER.decode(Objects.requireNonNull(request.getBody()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private RecordedRequest takeRequest() {
    try {
      System.out.println("[DEBUG] Taking request from server - Thread: " + Thread.currentThread().getName());
      RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS); // Increased timeout
      System.out.println("[DEBUG] Request taken: " + (request != null ? 
        "SUCCESS - Method: " + request.getMethod() : "TIMEOUT"));
      return request;
    } catch (InterruptedException e) {
      System.out.println("[DEBUG] takeRequest interrupted: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private void enqueueServerToAgentResponse(ServerToAgent response) {
    server.enqueue(getMockResponse(response));
  }

  @Nonnull
  private static MockResponse getMockResponse(ServerToAgent response) {
    Buffer bodyBuffer = new Buffer();
    bodyBuffer.write(response.encode());
    return new MockResponse.Builder().code(200).body(bodyBuffer).build();
  }

  private static RemoteConfigStatus getRemoteConfigStatus(RemoteConfigStatuses status) {
    return new RemoteConfigStatus.Builder().status(status).build();
  }

  private static AgentConfigMap createAgentConfigMap(String key, String content) {
    Map<String, AgentConfigFile> keyToFile = new HashMap<>();
    keyToFile.put(key, new AgentConfigFile.Builder().body(ByteString.encodeUtf8(content)).build());
    return new AgentConfigMap.Builder().config_map(keyToFile).build();
  }

  private static AgentDescription getAgentDescriptionWithOneIdentifyingValue(
      String key, String value) {
    KeyValue keyValue =
        new KeyValue.Builder()
            .key(key)
            .value(new AnyValue.Builder().string_value(value).build())
            .build();
    List<KeyValue> keyValues = new ArrayList<>();
    keyValues.add(keyValue);
    return new AgentDescription.Builder().identifying_attributes(keyValues).build();
  }

  private RecordedRequest initializeClient() {
    return initializeClient(new ServerToAgent.Builder().build());
  }

  private RecordedRequest initializeClient(ServerToAgent initialResponse) {
    System.out.println("[DEBUG] Initializing client with response: " + initialResponse);
    client = OpampClientImpl.create(requestService, state);

    // Prepare first request on start
    enqueueServerToAgentResponse(initialResponse);

    callbacks = spy(new TestCallbacks());
    System.out.println("[DEBUG] Starting client - Thread: " + Thread.currentThread().getName());
    client.start(callbacks);
    System.out.println("[DEBUG] Client started - taking request from server");
    RecordedRequest request = takeRequest();
    System.out.println("[DEBUG] Request taken: " + (request != null ? "SUCCESS" : "NULL"));
    return request;
  }

  private static class TestEffectiveConfig extends State.EffectiveConfig {
    private opamp.proto.EffectiveConfig config;

    public TestEffectiveConfig(opamp.proto.EffectiveConfig initialValue) {
      config = initialValue;
    }

    @Override
    public opamp.proto.EffectiveConfig get() {
      return config;
    }
  }

  private RequestService createHttpService() {
    return new TestHttpRequestService(
        HttpRequestService.create(OkHttpSender.create(server.url("/v1/opamp").toString())));
  }

  private static class TestHttpRequestService implements RequestService {
    private final HttpRequestService delegate;

    private TestHttpRequestService(HttpRequestService delegate) {
      this.delegate = delegate;
    }

    @Override
    public void start(Callback callback, Supplier<Request> requestSupplier) {
      delegate.start(callback, requestSupplier);
    }

    @Override
    public void sendRequest() {
      delegate.sendRequest();
    }

    @Override
    public void stop() {
      // This is to verify agent disconnect field presence for the websocket use case.
      delegate.sendRequest();
      delegate.stop();
    }
  }

  private static class TestCallbacks implements OpampClient.Callbacks {
    private final AtomicInteger onConnectCalls = new AtomicInteger();
    private final AtomicInteger onConnectFailedCalls = new AtomicInteger();
    private final AtomicInteger onErrorResponseCalls = new AtomicInteger();
    private final AtomicInteger onMessageCalls = new AtomicInteger();

    @Override
    public void onConnect() {
      int count = onConnectCalls.incrementAndGet();
      System.out.println("[DEBUG] TestCallbacks.onConnect() called - count: " + count + 
                         ", Thread: " + Thread.currentThread().getName() + 
                         ", Time: " + System.currentTimeMillis());
    }

    @Override
    public void onConnectFailed(@Nullable Throwable throwable) {
      int count = onConnectFailedCalls.incrementAndGet();
      System.out.println("[DEBUG] TestCallbacks.onConnectFailed() called - count: " + count + 
                         ", Exception: " + (throwable != null ? throwable.getMessage() : "null") +
                         ", Thread: " + Thread.currentThread().getName() + 
                         ", Time: " + System.currentTimeMillis());
    }

    @Override
    public void onErrorResponse(ServerErrorResponse errorResponse) {
      int count = onErrorResponseCalls.incrementAndGet();
      System.out.println("[DEBUG] TestCallbacks.onErrorResponse() called - count: " + count + 
                         ", Thread: " + Thread.currentThread().getName() + 
                         ", Time: " + System.currentTimeMillis());
    }

    @Override
    public void onMessage(MessageData messageData) {
      int count = onMessageCalls.incrementAndGet();
      System.out.println("[DEBUG] TestCallbacks.onMessage() called - count: " + count + 
                         ", Thread: " + Thread.currentThread().getName() + 
                         ", Time: " + System.currentTimeMillis() +
                         ", MessageData: " + messageData);
    }
  }
}
