/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.upstream;

import android.os.Handler;
import android.util.Log;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages Event Mode CMCD reporting: heartbeat scheduling and event-triggered dispatch.
 *
 * <p>Lifecycle is tied to a playback session. Created when a v2 configuration with event targets is
 * active; stopped when playback ends or media item changes.
 *
 * <p>Reports are sent as HTTP POST requests with body in LF-separated query-argument format
 * (comma-separated key=value, NOT URL-encoded, NO header group prefixes) and {@code Content-Type:
 * application/cmcd}.
 */
@UnstableApi
public final class CmcdEventReporter {

  private static final String TAG = "CmcdEventReporter";

  /** Content type for CMCD body transmission. */
  private static final String CONTENT_TYPE_CMCD = "application/cmcd";

  /** HTTP POST method. */
  private static final String HTTP_METHOD_POST = "POST";

  /** Default connection timeout in milliseconds. */
  private static final int CONNECTION_TIMEOUT_MS = 10_000;

  /** Default read timeout in milliseconds. */
  private static final int READ_TIMEOUT_MS = 10_000;

  /** Listener for Event Mode lifecycle callbacks. */
  public interface Listener {
    /** Called when an event report fails delivery to a target. */
    void onEventReportFailed(EventTarget target, Exception error);
  }

  private final CmcdV2Configuration configuration;
  private final Handler handler;
  private final Listener listener;
  private final ExecutorService httpExecutor;
  private final Runnable heartbeatRunnable;

  private boolean started;

  /**
   * Creates a new {@code CmcdEventReporter}.
   *
   * @param configuration The v2 configuration containing event targets and heartbeat interval.
   * @param handler The handler for scheduling heartbeat messages on the playback thread.
   * @param listener The listener for error callbacks.
   */
  public CmcdEventReporter(
      CmcdV2Configuration configuration, Handler handler, Listener listener) {
    this.configuration = configuration;
    this.handler = handler;
    this.listener = listener;
    this.httpExecutor = Executors.newSingleThreadExecutor();
    this.heartbeatRunnable = this::sendHeartbeatAndReschedule;
    this.started = false;
  }

  /** Starts heartbeat scheduling at the configured interval. */
  public void start() {
    if (started) {
      return;
    }
    started = true;
    if (configuration.heartbeatIntervalMs > 0 && !configuration.eventTargets.isEmpty()) {
      handler.postDelayed(heartbeatRunnable, configuration.heartbeatIntervalMs);
    }
  }

  /** Stops heartbeat scheduling and cancels pending reports. Shuts down the HTTP executor. */
  public void stop() {
    if (!started) {
      return;
    }
    started = false;
    handler.removeCallbacks(heartbeatRunnable);
    httpExecutor.shutdownNow();
  }

  /**
   * Reports an event-triggered CMCD payload to all applicable targets.
   *
   * <p>The event type and timestamp keys ({@code e} and {@code ts}) are ensured to be present in
   * the data before dispatch. Each target's allowed-event-type and allowed-key filters are applied.
   *
   * @param eventType The event type token (one of the 19 CTA-5004-B event types).
   * @param data The assembled CMCD v2 data to report.
   */
  public void reportEvent(@CmcdV2Keys.EventType String eventType, CmcdV2Data data) {
    if (!started || configuration.eventTargets.isEmpty()) {
      return;
    }
    dispatchToTargets(eventType, data);
  }

  /**
   * Reports a heartbeat CMCD payload to all applicable targets.
   *
   * <p>Uses event type {@link CmcdV2Keys#EVENT_TYPE_TIME_INTERVAL} ("t").
   *
   * @param data The assembled CMCD v2 data to report.
   */
  public void reportHeartbeat(CmcdV2Data data) {
    if (!started || configuration.eventTargets.isEmpty()) {
      return;
    }
    dispatchToTargets(CmcdV2Keys.EVENT_TYPE_TIME_INTERVAL, data);
  }

  // ==============================================================================================
  // Internal implementation
  // ==============================================================================================

  private void sendHeartbeatAndReschedule() {
    if (!started) {
      return;
    }
    // Build a minimal heartbeat data with event type and timestamp
    CmcdV2Data heartbeatData =
        new CmcdV2Data.Factory(configuration, CmcdData.STREAMING_FORMAT_DASH)
            .setEventType(CmcdV2Keys.EVENT_TYPE_TIME_INTERVAL)
            .setTimestampMs(System.currentTimeMillis())
            .createCmcdData();
    reportHeartbeat(heartbeatData);

    // Reschedule
    if (started && configuration.heartbeatIntervalMs > 0) {
      handler.postDelayed(heartbeatRunnable, configuration.heartbeatIntervalMs);
    }
  }

  /**
   * Dispatches data to all applicable event targets, applying per-target event and key filtering.
   */
  private void dispatchToTargets(
      @CmcdV2Keys.EventType String eventType, CmcdV2Data data) {
    for (EventTarget target : configuration.eventTargets) {
      // Event type filtering: skip target if event type not in allowed set (non-empty set)
      if (!target.allowedEventTypes.isEmpty()
          && !target.allowedEventTypes.contains(eventType)) {
        continue;
      }

      // Build filtered data view applying per-target key filtering
      CmcdKeyValueStore filteredData = applyKeyFilter(data, target.allowedKeys);

      // Ensure 'e' and 'ts' keys are present by creating a view that includes them
      CmcdKeyValueStore dataWithMetadata =
          ensureEventMetadata(filteredData, eventType, data);

      // Serialize to body format
      String body = CmcdSerializer.toBody(dataWithMetadata);
      if (body.isEmpty()) {
        continue;
      }

      // Send HTTP POST on background thread
      sendHttpPost(target, body);
    }
  }

  /**
   * Applies key filtering for a target. If the target's allowedKeys is empty, all keys pass
   * through. Otherwise, only keys in the allowed set are included.
   */
  private static CmcdKeyValueStore applyKeyFilter(
      CmcdKeyValueStore data, ImmutableSet<String> allowedKeys) {
    if (allowedKeys.isEmpty()) {
      return data;
    }
    return new CmcdKeyValueStore() {
      @Override
      public ImmutableMap<String, Object> getObjectKeys() {
        return filterMap(data.getObjectKeys(), allowedKeys);
      }

      @Override
      public ImmutableMap<String, Object> getRequestKeys() {
        return filterMap(data.getRequestKeys(), allowedKeys);
      }

      @Override
      public ImmutableMap<String, Object> getSessionKeys() {
        return filterMap(data.getSessionKeys(), allowedKeys);
      }

      @Override
      public ImmutableMap<String, Object> getStatusKeys() {
        return filterMap(data.getStatusKeys(), allowedKeys);
      }

      @Override
      public ImmutableMap<String, Object> getEventOnlyKeys() {
        return filterMap(data.getEventOnlyKeys(), allowedKeys);
      }
    };
  }

  /** Filters an ImmutableMap to only include keys present in the allowed set. */
  private static ImmutableMap<String, Object> filterMap(
      ImmutableMap<String, Object> map, ImmutableSet<String> allowedKeys) {
    if (map.isEmpty()) {
      return map;
    }
    ImmutableMap.Builder<String, Object> filtered = ImmutableMap.builder();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if (allowedKeys.contains(entry.getKey())) {
        filtered.put(entry);
      }
    }
    return filtered.buildOrThrow();
  }

  /**
   * Ensures the event metadata keys ({@code e} and {@code ts}) are present in the data. If the
   * original data already has these keys, they pass through (subject to key filtering). If not, we
   * inject them.
   */
  private static CmcdKeyValueStore ensureEventMetadata(
      CmcdKeyValueStore filteredData,
      @CmcdV2Keys.EventType String eventType,
      CmcdV2Data originalData) {
    ImmutableMap<String, Object> eventOnlyKeys = filteredData.getEventOnlyKeys();

    boolean hasEventType = eventOnlyKeys.containsKey(CmcdV2Keys.KEY_EVENT_TYPE);
    boolean hasTimestamp = eventOnlyKeys.containsKey(CmcdV2Keys.KEY_TIMESTAMP);

    if (hasEventType && hasTimestamp) {
      return filteredData;
    }

    // Build new event-only keys with e and ts ensured
    ImmutableMap.Builder<String, Object> newEventOnly = ImmutableMap.builder();
    newEventOnly.putAll(eventOnlyKeys);

    if (!hasEventType) {
      newEventOnly.put(CmcdV2Keys.KEY_EVENT_TYPE, eventType);
    }
    if (!hasTimestamp) {
      // Use current time if not already set in original data
      Object originalTs = originalData.getEventOnlyKeys().get(CmcdV2Keys.KEY_TIMESTAMP);
      long ts = (originalTs instanceof Long) ? (Long) originalTs : System.currentTimeMillis();
      newEventOnly.put(CmcdV2Keys.KEY_TIMESTAMP, ts);
    }

    ImmutableMap<String, Object> finalEventOnly = newEventOnly.buildOrThrow();
    return new CmcdKeyValueStore() {
      @Override
      public ImmutableMap<String, Object> getObjectKeys() {
        return filteredData.getObjectKeys();
      }

      @Override
      public ImmutableMap<String, Object> getRequestKeys() {
        return filteredData.getRequestKeys();
      }

      @Override
      public ImmutableMap<String, Object> getSessionKeys() {
        return filteredData.getSessionKeys();
      }

      @Override
      public ImmutableMap<String, Object> getStatusKeys() {
        return filteredData.getStatusKeys();
      }

      @Override
      public ImmutableMap<String, Object> getEventOnlyKeys() {
        return finalEventOnly;
      }
    };
  }

  /** Sends an HTTP POST request to the target on a background thread. */
  private void sendHttpPost(EventTarget target, String body) {
    httpExecutor.execute(
        () -> {
          HttpURLConnection connection = null;
          try {
            URL url = new URL(target.url);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(HTTP_METHOD_POST);
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", CONTENT_TYPE_CMCD);

            // Include Authorization header if access token is configured
            if (target.accessToken != null) {
              connection.setRequestProperty(
                  "Authorization", "Bearer " + target.accessToken);
            }

            // Write body
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bodyBytes.length);
            try (OutputStream os = connection.getOutputStream()) {
              os.write(bodyBytes);
              os.flush();
            }

            // Read response to complete the request
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
              throw new IOException(
                  "HTTP POST to " + target.url + " failed with response code: " + responseCode);
            }
          } catch (Exception e) {
            Log.w(TAG, "Event report failed for target: " + target.url, e);
            // Notify listener on the handler thread
            handler.post(() -> listener.onEventReportFailed(target, e));
          } finally {
            if (connection != null) {
              connection.disconnect();
            }
          }
        });
  }
}
