/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opamp.client.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.opamp.client.internal.request.service.RequestService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import opamp.proto.RemoteConfigStatus;
import opamp.proto.RemoteConfigStatuses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpampClientImplTest {

  private OpampClientImpl client;
  private RequestService mockRequestService;

  @BeforeEach
  void setUp() {
    mockRequestService = mock(RequestService.class);
    client = new OpampClientImpl(mockRequestService);
  }

  /**
   * Test that verifies the remote config status setter works correctly.
   * This test was failing sporadically with "Expecting actual not to be null" error
   * due to potential race conditions in the original implementation.
   */
  @Test
  void verifyRemoteConfigStatusSetter() {
    // Create a RemoteConfigStatus object to set
    RemoteConfigStatus expectedStatus = new RemoteConfigStatus.Builder()
        .status(RemoteConfigStatuses.RemoteConfigStatuses_APPLIED)
        .build();

    // Set the remote config status
    client.setRemoteConfigStatus(expectedStatus);

    // Verify the status was set correctly and is not null
    RemoteConfigStatus actualStatus = client.getRemoteConfigStatus();
    assertThat(actualStatus)
        .as("Remote config status should not be null after being set")
        .isNotNull();
    
    assertThat(actualStatus.status)
        .as("Remote config status should match the expected status")
        .isEqualTo(RemoteConfigStatuses.RemoteConfigStatuses_APPLIED);
  }

  @Test
  void verifyRemoteConfigStatusSetter_withNullValue() {
    // Set the remote config status to null
    client.setRemoteConfigStatus(null);

    // Verify the status is null
    RemoteConfigStatus actualStatus = client.getRemoteConfigStatus();
    assertThat(actualStatus)
        .as("Remote config status should be null when set to null")
        .isNull();
  }

  @Test
  void verifyRemoteConfigStatusSetter_multipleUpdates() {
    // Create first status
    RemoteConfigStatus firstStatus = new RemoteConfigStatus.Builder()
        .status(RemoteConfigStatuses.RemoteConfigStatuses_APPLIED)
        .build();

    // Create second status
    RemoteConfigStatus secondStatus = new RemoteConfigStatus.Builder()
        .status(RemoteConfigStatuses.RemoteConfigStatuses_FAILED)
        .build();

    // Set first status
    client.setRemoteConfigStatus(firstStatus);
    assertThat(client.getRemoteConfigStatus())
        .as("First status should be set correctly")
        .isNotNull()
        .satisfies(status -> assertThat(status.status).isEqualTo(RemoteConfigStatuses.RemoteConfigStatuses_APPLIED));

    // Set second status
    client.setRemoteConfigStatus(secondStatus);
    assertThat(client.getRemoteConfigStatus())
        .as("Second status should replace the first")
        .isNotNull()
        .satisfies(status -> assertThat(status.status).isEqualTo(RemoteConfigStatuses.RemoteConfigStatuses_FAILED));
  }

  /**
   * Test that attempts to reproduce race conditions by rapidly setting and getting the status.
   * This test runs the setter multiple times to try to catch sporadic failures.
   */
  @Test
  void verifyRemoteConfigStatusSetter_rapidSuccession() {
    RemoteConfigStatus status = new RemoteConfigStatus.Builder()
        .status(RemoteConfigStatuses.RemoteConfigStatuses_APPLIED)
        .build();

    // Rapidly set and get the status multiple times
    for (int i = 0; i < 1000; i++) {
      client.setRemoteConfigStatus(status);
      RemoteConfigStatus actualStatus = client.getRemoteConfigStatus();
      
      assertThat(actualStatus)
          .as("Remote config status should not be null on iteration " + i)
          .isNotNull();
      
      assertThat(actualStatus.status)
          .as("Remote config status should match expected on iteration " + i)
          .isEqualTo(RemoteConfigStatuses.RemoteConfigStatuses_APPLIED);
    }
  }

  /**
   * Multi-threaded test to verify thread safety of setRemoteConfigStatus.
   * This test attempts to reproduce the sporadic null pointer issues by 
   * accessing the status concurrently from multiple threads.
   */
  @Test
  void verifyRemoteConfigStatusSetter_concurrentAccess() throws InterruptedException {
    int numThreads = 10;
    int operationsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch completionLatch = new CountDownLatch(numThreads);
    AtomicInteger nullCount = new AtomicInteger(0);
    AtomicInteger unexpectedStatusCount = new AtomicInteger(0);

    RemoteConfigStatus expectedStatus = new RemoteConfigStatus.Builder()
        .status(RemoteConfigStatuses.RemoteConfigStatuses_APPLIED)
        .build();

    // Create multiple threads that will set and get the status concurrently
    for (int i = 0; i < numThreads; i++) {
      @SuppressWarnings("unused")
      Future<?> ignored = executor.submit(() -> {
        try {
          startLatch.await(); // Wait for all threads to be ready
          
          for (int j = 0; j < operationsPerThread; j++) {
            // Set the status
            client.setRemoteConfigStatus(expectedStatus);
            
            // Immediately get and verify the status
            RemoteConfigStatus actualStatus = client.getRemoteConfigStatus();
            
            if (actualStatus == null) {
              nullCount.incrementAndGet();
            } else if (!RemoteConfigStatuses.RemoteConfigStatuses_APPLIED.equals(actualStatus.status)) {
              unexpectedStatusCount.incrementAndGet();
            }
            
            // Small delay to allow for more interleaving
            try {
              Thread.sleep(1);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              break;
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          completionLatch.countDown();
        }
      });
    }

    // Start all threads at once
    startLatch.countDown();
    
    // Wait for all threads to complete
    assertThat(completionLatch.await(30, TimeUnit.SECONDS))
        .as("All threads should complete within 30 seconds")
        .isTrue();
    
    executor.shutdown();

    // Verify no null values were observed (this is where the sporadic failure would occur)
    assertThat(nullCount.get())
        .as("No null values should be observed during concurrent access")
        .isZero();
    
    assertThat(unexpectedStatusCount.get())
        .as("No unexpected status values should be observed during concurrent access")
        .isZero();
  }

  /**
   * Test concurrent setting of different status values to ensure consistency.
   */
  @Test
  void verifyRemoteConfigStatusSetter_concurrentDifferentValues() throws InterruptedException {
    int numThreads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch completionLatch = new CountDownLatch(numThreads);

    RemoteConfigStatus[] statuses = {
        new RemoteConfigStatus.Builder().status(RemoteConfigStatuses.RemoteConfigStatuses_APPLIED).build(),
        new RemoteConfigStatus.Builder().status(RemoteConfigStatuses.RemoteConfigStatuses_APPLYING).build(),
        new RemoteConfigStatus.Builder().status(RemoteConfigStatuses.RemoteConfigStatuses_FAILED).build(),
        new RemoteConfigStatus.Builder().status(RemoteConfigStatuses.RemoteConfigStatuses_UNSET).build()
    };

    for (int i = 0; i < numThreads; i++) {
      int threadIndex = i;
      @SuppressWarnings("unused")
      Future<?> ignored = executor.submit(() -> {
        try {
          startLatch.await();
          
          for (int j = 0; j < 100; j++) {
            client.setRemoteConfigStatus(statuses[threadIndex]);
            RemoteConfigStatus actualStatus = client.getRemoteConfigStatus();
            
            // The status should never be null
            assertThat(actualStatus)
                .as("Status should never be null in thread " + threadIndex + " iteration " + j)
                .isNotNull();
            
            // The status should be one of the valid values we set
            assertThat(actualStatus.status)
                .as("Status should be one of the expected values")
                .isIn(
                    RemoteConfigStatuses.RemoteConfigStatuses_APPLIED,
                    RemoteConfigStatuses.RemoteConfigStatuses_APPLYING,
                    RemoteConfigStatuses.RemoteConfigStatuses_FAILED,
                    RemoteConfigStatuses.RemoteConfigStatuses_UNSET
                );
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          completionLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    assertThat(completionLatch.await(30, TimeUnit.SECONDS))
        .as("All threads should complete within 30 seconds")
        .isTrue();
    
    executor.shutdown();

    // Final check - the status should still not be null
    assertThat(client.getRemoteConfigStatus())
        .as("Final status should not be null")
        .isNotNull();
  }
}