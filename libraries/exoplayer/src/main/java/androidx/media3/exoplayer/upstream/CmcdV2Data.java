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

import static com.google.common.base.Preconditions.checkNotNull;

import android.net.Uri;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Map;

/**
 * Holds assembled CMCD v2 (CTA-5004-B) key-value data, grouped by header category.
 *
 * <p>Instances are created via {@link Factory} and implement {@link CmcdKeyValueStore} so they can
 * be serialized through {@link CmcdSerializer}.
 *
 * <p>Only keys that were explicitly set on the factory are included in the output maps. Unset keys
 * are omitted entirely (no defaults or zero values).
 */
@UnstableApi
public final class CmcdV2Data implements CmcdKeyValueStore {

  /** Sentinel value indicating a long field has not been set. */
  private static final long LONG_UNSET = C.TIME_UNSET;

  /** Sentinel value indicating an int field has not been set. */
  private static final int INT_UNSET = C.INDEX_UNSET;

  // =============================================================================================
  // Immutable data fields (grouped by header)
  // =============================================================================================

  private final ImmutableMap<String, Object> objectKeys;
  private final ImmutableMap<String, Object> requestKeys;
  private final ImmutableMap<String, Object> sessionKeys;
  private final ImmutableMap<String, Object> statusKeys;
  private final ImmutableMap<String, Object> eventOnlyKeys;

  private CmcdV2Data(
      ImmutableMap<String, Object> objectKeys,
      ImmutableMap<String, Object> requestKeys,
      ImmutableMap<String, Object> sessionKeys,
      ImmutableMap<String, Object> statusKeys,
      ImmutableMap<String, Object> eventOnlyKeys) {
    this.objectKeys = objectKeys;
    this.requestKeys = requestKeys;
    this.sessionKeys = sessionKeys;
    this.statusKeys = statusKeys;
    this.eventOnlyKeys = eventOnlyKeys;
  }

  @Override
  public ImmutableMap<String, Object> getObjectKeys() {
    return objectKeys;
  }

  @Override
  public ImmutableMap<String, Object> getRequestKeys() {
    return requestKeys;
  }

  @Override
  public ImmutableMap<String, Object> getSessionKeys() {
    return sessionKeys;
  }

  @Override
  public ImmutableMap<String, Object> getStatusKeys() {
    return statusKeys;
  }

  @Override
  public ImmutableMap<String, Object> getEventOnlyKeys() {
    return eventOnlyKeys;
  }

  // =============================================================================================
  // DataSpec attachment
  // =============================================================================================

  /**
   * Adds CMCD v2 data to the provided {@link DataSpec}, routing to the correct serialization path
   * based on the version mode in the given configuration.
   *
   * <p>In mixed-version mode ({@link CmcdV2Configuration#useV1SyntaxForRequestMode} is {@code
   * true}), the Request Mode data is serialized without the {@code v} key and uses v1 {@code
   * nor}/{@code nrr} format. In v2-only mode, data is serialized with {@code v=2} and v2 inner
   * list {@code nor} format.
   *
   * <p>Event-only keys are never attached to the DataSpec (they are only used in Event Mode body
   * transmission).
   *
   * @param dataSpec The {@link DataSpec} to attach CMCD data to.
   * @param configuration The {@link CmcdV2Configuration} controlling version mode and transmission
   *     mode.
   * @return A new {@link DataSpec} with CMCD data attached.
   */
  @CheckResult
  public DataSpec addToDataSpec(DataSpec dataSpec, CmcdV2Configuration configuration) {
    CmcdKeyValueStore requestView;
    if (configuration.useV1SyntaxForRequestMode) {
      requestView = createV1RequestView();
    } else {
      requestView = this;
    }

    if (configuration.dataTransmissionMode == CmcdConfiguration.MODE_REQUEST_HEADER) {
      ImmutableMap<String, String> headers = CmcdSerializer.toHeaders(requestView);
      if (!headers.isEmpty()) {
        dataSpec = dataSpec.withAdditionalHeaders(headers);
      }
    } else if (configuration.dataTransmissionMode == CmcdConfiguration.MODE_QUERY_PARAMETER) {
      // Build a raw (unencoded) query value and use appendQueryParameter which handles encoding.
      String rawQueryValue = CmcdSerializer.toRawQueryParameter(requestView);
      if (!rawQueryValue.isEmpty()) {
        Uri.Builder uriBuilder =
            dataSpec
                .uri
                .buildUpon()
                .appendQueryParameter(CmcdConfiguration.CMCD_QUERY_PARAMETER_KEY, rawQueryValue);
        dataSpec = dataSpec.buildUpon().setUri(uriBuilder.build()).build();
      }
    }

    return dataSpec;
  }

  /**
   * Creates a v1-compatible "request view" of this data for mixed-version mode.
   *
   * <p>The view omits the {@code v} key from session keys and converts inner list {@code nor}
   * values to v1 scalar format with a separate {@code nrr} key.
   */
  private CmcdKeyValueStore createV1RequestView() {
    // Filter out the "v" key from session keys for v1 request mode
    ImmutableMap<String, Object> filteredSessionKeys;
    if (sessionKeys.containsKey(CmcdConfiguration.KEY_VERSION)) {
      ImmutableMap.Builder<String, Object> sessionBuilder = ImmutableMap.builder();
      for (Map.Entry<String, Object> entry : sessionKeys.entrySet()) {
        if (!entry.getKey().equals(CmcdConfiguration.KEY_VERSION)) {
          sessionBuilder.put(entry);
        }
      }
      filteredSessionKeys = sessionBuilder.buildOrThrow();
    } else {
      filteredSessionKeys = sessionKeys;
    }

    // Convert inner list nor to v1 scalar format with separate nrr key
    ImmutableMap<String, Object> adjustedRequestKeys;
    Object norValue = requestKeys.get(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST);
    if (norValue instanceof InnerListValue) {
      InnerListValue norList = (InnerListValue) norValue;
      ImmutableMap.Builder<String, Object> requestBuilder = ImmutableMap.builder();
      for (Map.Entry<String, Object> entry : requestKeys.entrySet()) {
        if (!entry.getKey().equals(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST)) {
          requestBuilder.put(entry);
        }
      }
      // Convert the first item in the inner list to a scalar quoted string (v1 format)
      if (!norList.items.isEmpty()) {
        InnerListValue.Item firstItem = norList.items.get(0);
        String norString = String.valueOf(firstItem.value);
        requestBuilder.put(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST, norString);
        // If the first item has a range parameter, add nrr as a separate key
        if (firstItem.rangeParam != null) {
          requestBuilder.put(CmcdConfiguration.KEY_NEXT_RANGE_REQUEST, firstItem.rangeParam);
        }
      }
      adjustedRequestKeys = requestBuilder.buildOrThrow();
    } else {
      adjustedRequestKeys = requestKeys;
    }

    // Return a CmcdKeyValueStore view with the filtered data (no event-only keys for request mode)
    ImmutableMap<String, Object> finalFilteredSessionKeys = filteredSessionKeys;
    ImmutableMap<String, Object> finalAdjustedRequestKeys = adjustedRequestKeys;
    return new CmcdKeyValueStore() {
      @Override
      public ImmutableMap<String, Object> getObjectKeys() {
        return objectKeys;
      }

      @Override
      public ImmutableMap<String, Object> getRequestKeys() {
        return finalAdjustedRequestKeys;
      }

      @Override
      public ImmutableMap<String, Object> getSessionKeys() {
        return finalFilteredSessionKeys;
      }

      @Override
      public ImmutableMap<String, Object> getStatusKeys() {
        return statusKeys;
      }

      @Override
      public ImmutableMap<String, Object> getEventOnlyKeys() {
        return ImmutableMap.of();
      }
    };
  }

  // =============================================================================================
  // Factory
  // =============================================================================================

  /**
   * Factory for building {@link CmcdV2Data} instances.
   *
   * <p>Supports all v1 key setters (scalar) plus new v2 key setters including inner list variants.
   * When both a scalar and inner list version of a key are set, the inner list version takes
   * priority.
   *
   * <p>Automatically includes {@code v=2} in the session group.
   */
  public static final class Factory {

    private final CmcdV2Configuration configuration;
    private final @CmcdData.StreamingFormat String streamingFormat;

    // --- V1 scalar fields (Object group) ---
    private int bitrateKbps;
    private int topBitrateKbps;
    private long objectDurationMs;
    @Nullable private @CmcdData.ObjectType String objectType;

    // --- V1 scalar fields (Request group) ---
    private long bufferLengthMs;
    private long measuredThroughputKbps;
    private long deadlineMs;
    private boolean startup;
    @Nullable private String nextObjectRequest;
    @Nullable private String nextRangeRequest;

    // --- V1 scalar fields (Session group) ---
    // contentId and sessionId come from configuration
    private float playbackRate;
    @Nullable private @CmcdData.StreamType String streamType;

    // --- V1 scalar fields (Status group) ---
    private int requestedMaximumThroughputKbps;
    private boolean bufferStarvation;
    private boolean bufferStarvationSet;

    // --- V2 inner list variants of v1 keys ---
    @Nullable private InnerListValue bitratePerType;
    @Nullable private InnerListValue bufferLengthPerType;
    @Nullable private InnerListValue measuredThroughputPerType;
    @Nullable private InnerListValue topBitratePerType;
    @Nullable private InnerListValue nextObjectRequestList;

    // --- V2 Event keys ---
    @Nullable private @CmcdV2Keys.EventType String eventType;
    private long timestampMs;
    @Nullable private @CmcdV2Keys.StateType String state;

    // --- V2 Quality keys ---
    private long droppedFramesAccumulated;
    private long liveLatencyMs;
    private long mediaStartDelayMs;
    @Nullable private InnerListValue playheadBitratePerType;
    private long playheadTimeMs;

    // --- V2 Buffer keys ---
    @Nullable private InnerListValue bufferStarvationCount;
    @Nullable private InnerListValue bufferStarvationDuration;
    @Nullable private InnerListValue bufferStarvationDurationAccumulated;

    // --- V2 Response keys (Event only) ---
    private long timeToFirstByteMs;
    private long timeToFirstBodyByteMs;
    private long timeToLastByteMs;
    private int responseCode;
    @Nullable private String smrtDataHeader;
    @Nullable private String cmsdDynamicHeader;
    @Nullable private String cmsdStaticHeader;
    @Nullable private String requestedUrl;

    // --- V2 Session extension keys ---
    @Nullable private String customEventName;
    @Nullable private String hostname;
    private boolean nonRendered;
    private boolean nonRenderedSet;
    private long sequenceNumber;
    @Nullable private String contentSignature;

    // --- V2 Bitrate keys (Object group, inner list) ---
    @Nullable private InnerListValue lowestAggregatedBitratePerType;
    @Nullable private InnerListValue lowestEncodedBitratePerType;
    @Nullable private InnerListValue topAggregatedBitratePerType;
    @Nullable private InnerListValue targetBufferLengthPerType;
    @Nullable private InnerListValue topPlayableBitratePerType;
    @Nullable private InnerListValue aggregateEncodedBitratePerType;

    // --- V2 Status keys ---
    private boolean backgrounded;
    private boolean backgroundedSet;
    @Nullable private InnerListValue playerErrorCode;

    /**
     * Creates an instance.
     *
     * @param configuration The {@link CmcdV2Configuration} for this session.
     * @param streamingFormat The streaming format of the media content.
     */
    public Factory(
        CmcdV2Configuration configuration,
        @CmcdData.StreamingFormat String streamingFormat) {
      this.configuration = checkNotNull(configuration);
      this.streamingFormat = checkNotNull(streamingFormat);

      // Initialize sentinel values for unset fields
      this.bitrateKbps = C.RATE_UNSET_INT;
      this.topBitrateKbps = C.RATE_UNSET_INT;
      this.objectDurationMs = LONG_UNSET;
      this.bufferLengthMs = LONG_UNSET;
      this.measuredThroughputKbps = C.RATE_UNSET_INT;
      this.deadlineMs = LONG_UNSET;
      this.playbackRate = C.RATE_UNSET;
      this.requestedMaximumThroughputKbps = C.RATE_UNSET_INT;

      this.timestampMs = LONG_UNSET;
      this.droppedFramesAccumulated = LONG_UNSET;
      this.liveLatencyMs = LONG_UNSET;
      this.mediaStartDelayMs = LONG_UNSET;
      this.playheadTimeMs = LONG_UNSET;

      this.timeToFirstByteMs = LONG_UNSET;
      this.timeToFirstBodyByteMs = LONG_UNSET;
      this.timeToLastByteMs = LONG_UNSET;
      this.responseCode = INT_UNSET;

      this.sequenceNumber = LONG_UNSET;
    }

    // ===========================================================================================
    // V1 compatible setters (scalar)
    // ===========================================================================================

    /**
     * Sets the encoded bitrate in kbps of the object being requested.
     *
     * @param bitrateKbps The bitrate in kbps, or {@link C#RATE_UNSET_INT} to unset.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setBitrate(int bitrateKbps) {
      this.bitrateKbps = bitrateKbps;
      return this;
    }

    /**
     * Sets the top bitrate in kbps.
     *
     * @param topBitrateKbps The top bitrate in kbps, or {@link C#RATE_UNSET_INT} to unset.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTopBitrate(int topBitrateKbps) {
      this.topBitrateKbps = topBitrateKbps;
      return this;
    }

    /**
     * Sets the object duration in milliseconds.
     *
     * @param objectDurationMs The duration in ms, or {@link C#TIME_UNSET} to unset.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setObjectDurationMs(long objectDurationMs) {
      this.objectDurationMs = objectDurationMs;
      return this;
    }

    /**
     * Sets the object type.
     *
     * @param objectType The object type token.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setObjectType(@Nullable @CmcdData.ObjectType String objectType) {
      this.objectType = objectType;
      return this;
    }

    /**
     * Sets the buffer length in milliseconds.
     *
     * @param bufferLengthMs The buffer length in ms, or {@link C#TIME_UNSET} to unset.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setBufferLength(long bufferLengthMs) {
      this.bufferLengthMs = bufferLengthMs;
      return this;
    }

    /**
     * Sets the measured throughput in kbps.
     *
     * @param measuredThroughputKbps The throughput in kbps, or {@link C#RATE_UNSET_INT} to unset.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setMeasuredThroughput(long measuredThroughputKbps) {
      this.measuredThroughputKbps = measuredThroughputKbps;
      return this;
    }

    /**
     * Sets the deadline in milliseconds.
     *
     * @param deadlineMs The deadline in ms, or {@link C#TIME_UNSET} to unset.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setDeadline(long deadlineMs) {
      this.deadlineMs = deadlineMs;
      return this;
    }

    /**
     * Sets the startup flag.
     *
     * @param startup Whether the request is for startup/recovery.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setStartup(boolean startup) {
      this.startup = startup;
      return this;
    }

    /**
     * Sets the next object request path.
     *
     * @param nextObjectRequest The relative path of the next object, or {@code null} to unset.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setNextObjectRequest(@Nullable String nextObjectRequest) {
      this.nextObjectRequest = nextObjectRequest;
      return this;
    }

    /**
     * Sets the next range request.
     *
     * @param nextRangeRequest The byte range, or {@code null} to unset.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setNextRangeRequest(@Nullable String nextRangeRequest) {
      this.nextRangeRequest = nextRangeRequest;
      return this;
    }

    /**
     * Sets the playback rate.
     *
     * @param playbackRate The playback rate, or {@link C#RATE_UNSET} to unset.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setPlaybackRate(float playbackRate) {
      this.playbackRate = playbackRate;
      return this;
    }

    /**
     * Sets the stream type.
     *
     * @param streamType The stream type token ("v" for VOD, "l" for live).
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setStreamType(@Nullable @CmcdData.StreamType String streamType) {
      this.streamType = streamType;
      return this;
    }

    /**
     * Sets the requested maximum throughput in kbps.
     *
     * @param requestedMaximumThroughputKbps The requested max throughput, or {@link
     *     C#RATE_UNSET_INT} to unset.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setRequestedMaximumThroughput(int requestedMaximumThroughputKbps) {
      this.requestedMaximumThroughputKbps = requestedMaximumThroughputKbps;
      return this;
    }

    /**
     * Sets the buffer starvation flag.
     *
     * @param bufferStarvation Whether buffer starvation occurred.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setBufferStarvation(boolean bufferStarvation) {
      this.bufferStarvation = bufferStarvation;
      this.bufferStarvationSet = true;
      return this;
    }

    // ===========================================================================================
    // V2 inner list variants of v1 keys (take priority over scalar if both set)
    // ===========================================================================================

    /**
     * Sets the bitrate as an inner list with per-type values.
     *
     * <p>Takes priority over {@link #setBitrate(int)} if both are set.
     *
     * @param bitratePerType The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setBitratePerType(InnerListValue bitratePerType) {
      this.bitratePerType = checkNotNull(bitratePerType);
      return this;
    }

    /**
     * Sets the buffer length as an inner list with per-type values.
     *
     * <p>Takes priority over {@link #setBufferLength(long)} if both are set.
     *
     * @param bufferLengthPerType The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setBufferLengthPerType(InnerListValue bufferLengthPerType) {
      this.bufferLengthPerType = checkNotNull(bufferLengthPerType);
      return this;
    }

    /**
     * Sets the measured throughput as an inner list with per-type values.
     *
     * <p>Takes priority over {@link #setMeasuredThroughput(long)} if both are set.
     *
     * @param mtpPerType The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setMeasuredThroughputPerType(InnerListValue mtpPerType) {
      this.measuredThroughputPerType = checkNotNull(mtpPerType);
      return this;
    }

    /**
     * Sets the top bitrate as an inner list with per-type values.
     *
     * <p>Takes priority over {@link #setTopBitrate(int)} if both are set.
     *
     * @param tbPerType The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTopBitratePerType(InnerListValue tbPerType) {
      this.topBitratePerType = checkNotNull(tbPerType);
      return this;
    }

    /**
     * Sets the next object request as an inner list (v2 format for multiple next objects).
     *
     * <p>Takes priority over {@link #setNextObjectRequest(String)} if both are set.
     *
     * @param norList The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setNextObjectRequestList(InnerListValue norList) {
      this.nextObjectRequestList = checkNotNull(norList);
      return this;
    }

    // ===========================================================================================
    // V2 Event setters
    // ===========================================================================================

    /**
     * Sets the event type.
     *
     * @param eventType The event type token.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setEventType(@CmcdV2Keys.EventType String eventType) {
      this.eventType = checkNotNull(eventType);
      return this;
    }

    /**
     * Sets the timestamp in milliseconds.
     *
     * @param timestampMs The timestamp in ms.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTimestampMs(long timestampMs) {
      this.timestampMs = timestampMs;
      return this;
    }

    /**
     * Sets the player state.
     *
     * @param state The state type token.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setState(@CmcdV2Keys.StateType String state) {
      this.state = checkNotNull(state);
      return this;
    }

    // ===========================================================================================
    // V2 Quality setters
    // ===========================================================================================

    /**
     * Sets the accumulated dropped frames count.
     *
     * @param droppedFrames The accumulated dropped frames count.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setDroppedFramesAccumulated(long droppedFrames) {
      this.droppedFramesAccumulated = droppedFrames;
      return this;
    }

    /**
     * Sets the live latency in milliseconds.
     *
     * @param liveLatencyMs The live latency in ms.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setLiveLatencyMs(long liveLatencyMs) {
      this.liveLatencyMs = liveLatencyMs;
      return this;
    }

    /**
     * Sets the media start delay in milliseconds.
     *
     * @param mediaStartDelayMs The media start delay in ms.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setMediaStartDelayMs(long mediaStartDelayMs) {
      this.mediaStartDelayMs = mediaStartDelayMs;
      return this;
    }

    /**
     * Sets the playhead bitrate as an inner list with per-type values.
     *
     * @param pb The inner list value for playhead bitrate.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setPlayheadBitratePerType(InnerListValue pb) {
      this.playheadBitratePerType = checkNotNull(pb);
      return this;
    }

    /**
     * Sets the playhead time in milliseconds.
     *
     * @param playheadTimeMs The playhead time in ms.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setPlayheadTimeMs(long playheadTimeMs) {
      this.playheadTimeMs = playheadTimeMs;
      return this;
    }

    // ===========================================================================================
    // V2 Buffer setters
    // ===========================================================================================

    /**
     * Sets the buffer starvation count as an inner list.
     *
     * @param bsa The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setBufferStarvationCount(InnerListValue bsa) {
      this.bufferStarvationCount = checkNotNull(bsa);
      return this;
    }

    /**
     * Sets the buffer starvation duration as an inner list.
     *
     * @param bsd The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setBufferStarvationDuration(InnerListValue bsd) {
      this.bufferStarvationDuration = checkNotNull(bsd);
      return this;
    }

    /**
     * Sets the buffer starvation duration accumulated as an inner list.
     *
     * @param bsda The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setBufferStarvationDurationAccumulated(InnerListValue bsda) {
      this.bufferStarvationDurationAccumulated = checkNotNull(bsda);
      return this;
    }

    // ===========================================================================================
    // V2 Response setters (Event only)
    // ===========================================================================================

    /**
     * Sets the time to first byte in milliseconds.
     *
     * @param ttfbMs The time to first byte in ms.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTimeToFirstByteMs(long ttfbMs) {
      this.timeToFirstByteMs = ttfbMs;
      return this;
    }

    /**
     * Sets the time to first body byte in milliseconds.
     *
     * @param ttfbbMs The time to first body byte in ms.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTimeToFirstBodyByteMs(long ttfbbMs) {
      this.timeToFirstBodyByteMs = ttfbbMs;
      return this;
    }

    /**
     * Sets the time to last byte in milliseconds.
     *
     * @param ttlbMs The time to last byte in ms.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTimeToLastByteMs(long ttlbMs) {
      this.timeToLastByteMs = ttlbMs;
      return this;
    }

    /**
     * Sets the HTTP response code.
     *
     * @param responseCode The response code.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setResponseCode(int responseCode) {
      this.responseCode = responseCode;
      return this;
    }

    /**
     * Sets the SMRT data header (Base64 encoded).
     *
     * @param smrt The SMRT data header value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setSmrtDataHeader(String smrt) {
      this.smrtDataHeader = checkNotNull(smrt);
      return this;
    }

    /**
     * Sets the CMSD Dynamic header (Base64 encoded).
     *
     * @param cmsdd The CMSD Dynamic header value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setCmsdDynamicHeader(String cmsdd) {
      this.cmsdDynamicHeader = checkNotNull(cmsdd);
      return this;
    }

    /**
     * Sets the CMSD Static header (Base64 encoded).
     *
     * @param cmsds The CMSD Static header value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setCmsdStaticHeader(String cmsds) {
      this.cmsdStaticHeader = checkNotNull(cmsds);
      return this;
    }

    /**
     * Sets the requested URL.
     *
     * @param url The requested URL.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setRequestedUrl(String url) {
      this.requestedUrl = checkNotNull(url);
      return this;
    }

    // ===========================================================================================
    // V2 Session extension setters
    // ===========================================================================================

    /**
     * Sets the custom event name.
     *
     * @param cen The custom event name (max 64 characters).
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setCustomEventName(String cen) {
      this.customEventName = checkNotNull(cen);
      return this;
    }

    /**
     * Sets the hostname.
     *
     * @param hostname The hostname (max 128 characters).
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setHostname(String hostname) {
      this.hostname = checkNotNull(hostname);
      return this;
    }

    /**
     * Sets the non-rendered flag.
     *
     * @param nonRendered Whether content is not rendered as audio/video.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setNonRendered(boolean nonRendered) {
      this.nonRendered = nonRendered;
      this.nonRenderedSet = true;
      return this;
    }

    /**
     * Sets the sequence number.
     *
     * @param sequenceNumber The monotonically increasing sequence number.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setSequenceNumber(long sequenceNumber) {
      this.sequenceNumber = sequenceNumber;
      return this;
    }

    /**
     * Sets the content signature.
     *
     * @param contentSignature The content signature value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setContentSignature(String contentSignature) {
      this.contentSignature = checkNotNull(contentSignature);
      return this;
    }

    // ===========================================================================================
    // V2 Bitrate setters (Object group, inner list)
    // ===========================================================================================

    /**
     * Sets the lowest aggregated bitrate as an inner list with per-type values.
     *
     * @param lab The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setLowestAggregatedBitratePerType(InnerListValue lab) {
      this.lowestAggregatedBitratePerType = checkNotNull(lab);
      return this;
    }

    /**
     * Sets the lowest encoded bitrate as an inner list with per-type values.
     *
     * @param lb The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setLowestEncodedBitratePerType(InnerListValue lb) {
      this.lowestEncodedBitratePerType = checkNotNull(lb);
      return this;
    }

    /**
     * Sets the top aggregated bitrate as an inner list with per-type values.
     *
     * @param tab The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTopAggregatedBitratePerType(InnerListValue tab) {
      this.topAggregatedBitratePerType = checkNotNull(tab);
      return this;
    }

    /**
     * Sets the target buffer length as an inner list with per-type values.
     *
     * @param tbl The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTargetBufferLengthPerType(InnerListValue tbl) {
      this.targetBufferLengthPerType = checkNotNull(tbl);
      return this;
    }

    /**
     * Sets the top playable bitrate as an inner list with per-type values.
     *
     * @param tpb The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setTopPlayableBitratePerType(InnerListValue tpb) {
      this.topPlayableBitratePerType = checkNotNull(tpb);
      return this;
    }

    /**
     * Sets the aggregate encoded bitrate as an inner list with per-type values.
     *
     * @param ab The inner list value.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setAggregateEncodedBitratePerType(InnerListValue ab) {
      this.aggregateEncodedBitratePerType = checkNotNull(ab);
      return this;
    }

    // ===========================================================================================
    // V2 Status setters
    // ===========================================================================================

    /**
     * Sets the backgrounded flag.
     *
     * @param backgrounded Whether the player is backgrounded.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setBackgrounded(boolean backgrounded) {
      this.backgrounded = backgrounded;
      this.backgroundedSet = true;
      return this;
    }

    /**
     * Sets the player error code as an inner list.
     *
     * @param ec The inner list value for the error code.
     * @return This factory, for convenience.
     */
    @CanIgnoreReturnValue
    public Factory setPlayerErrorCode(InnerListValue ec) {
      this.playerErrorCode = checkNotNull(ec);
      return this;
    }

    // ===========================================================================================
    // Build method
    // ===========================================================================================

    /**
     * Creates an immutable {@link CmcdV2Data} from the current factory state.
     *
     * <p>Automatically includes {@code v=2} in the session group. Only keys that have been
     * explicitly set are included in the output.
     *
     * @return A new {@link CmcdV2Data} instance.
     */
    public CmcdV2Data createCmcdData() {
      return new CmcdV2Data(
          buildObjectKeys(),
          buildRequestKeys(),
          buildSessionKeys(),
          buildStatusKeys(),
          buildEventOnlyKeys());
    }

    // ===========================================================================================
    // Private build helpers
    // ===========================================================================================

    private ImmutableMap<String, Object> buildObjectKeys() {
      ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

      // br: inner list takes priority over scalar
      if (bitratePerType != null) {
        builder.put(CmcdConfiguration.KEY_BITRATE, bitratePerType);
      } else if (bitrateKbps != C.RATE_UNSET_INT) {
        builder.put(CmcdConfiguration.KEY_BITRATE, bitrateKbps);
      }

      // d (object duration)
      if (objectDurationMs != LONG_UNSET) {
        builder.put(CmcdConfiguration.KEY_OBJECT_DURATION, objectDurationMs);
      }

      // ot (object type)
      if (objectType != null) {
        builder.put(CmcdConfiguration.KEY_OBJECT_TYPE, objectType);
      }

      // tb: inner list takes priority over scalar
      if (topBitratePerType != null) {
        builder.put(CmcdConfiguration.KEY_TOP_BITRATE, topBitratePerType);
      } else if (topBitrateKbps != C.RATE_UNSET_INT) {
        builder.put(CmcdConfiguration.KEY_TOP_BITRATE, topBitrateKbps);
      }

      // V2 Bitrate keys (Object group)
      if (lowestAggregatedBitratePerType != null) {
        builder.put(CmcdV2Keys.KEY_LOWEST_AGGREGATED_BITRATE, lowestAggregatedBitratePerType);
      }
      if (lowestEncodedBitratePerType != null) {
        builder.put(CmcdV2Keys.KEY_LOWEST_ENCODED_BITRATE, lowestEncodedBitratePerType);
      }
      if (topAggregatedBitratePerType != null) {
        builder.put(CmcdV2Keys.KEY_TOP_AGGREGATED_BITRATE, topAggregatedBitratePerType);
      }
      if (topPlayableBitratePerType != null) {
        builder.put(CmcdV2Keys.KEY_TOP_PLAYABLE_BITRATE, topPlayableBitratePerType);
      }
      if (aggregateEncodedBitratePerType != null) {
        builder.put(CmcdV2Keys.KEY_AGGREGATE_ENCODED_BITRATE, aggregateEncodedBitratePerType);
      }

      return builder.buildOrThrow();
    }

    private ImmutableMap<String, Object> buildRequestKeys() {
      ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

      // bl: inner list takes priority over scalar
      if (bufferLengthPerType != null) {
        builder.put(CmcdConfiguration.KEY_BUFFER_LENGTH, bufferLengthPerType);
      } else if (bufferLengthMs != LONG_UNSET) {
        builder.put(CmcdConfiguration.KEY_BUFFER_LENGTH, bufferLengthMs);
      }

      // mtp: inner list takes priority over scalar
      if (measuredThroughputPerType != null) {
        builder.put(CmcdConfiguration.KEY_MEASURED_THROUGHPUT, measuredThroughputPerType);
      } else if (measuredThroughputKbps != C.RATE_UNSET_INT) {
        builder.put(CmcdConfiguration.KEY_MEASURED_THROUGHPUT, measuredThroughputKbps);
      }

      // dl (deadline)
      if (deadlineMs != LONG_UNSET) {
        builder.put(CmcdConfiguration.KEY_DEADLINE, deadlineMs);
      }

      // su (startup) - only include if true
      if (startup) {
        builder.put(CmcdConfiguration.KEY_STARTUP, true);
      }

      // nor: inner list takes priority over scalar
      if (nextObjectRequestList != null) {
        builder.put(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST, nextObjectRequestList);
      } else if (nextObjectRequest != null) {
        builder.put(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST, nextObjectRequest);
      }

      // nrr: only include if scalar nor is used (inner list nor embeds ranges inline)
      if (nextObjectRequestList == null && nextRangeRequest != null) {
        builder.put(CmcdConfiguration.KEY_NEXT_RANGE_REQUEST, nextRangeRequest);
      }

      // V2 Request keys
      if (state != null) {
        builder.put(CmcdV2Keys.KEY_STATE, state);
      }
      if (droppedFramesAccumulated != LONG_UNSET) {
        builder.put(CmcdV2Keys.KEY_DROPPED_FRAMES_ACCUMULATED, droppedFramesAccumulated);
      }
      if (playheadBitratePerType != null) {
        builder.put(CmcdV2Keys.KEY_PLAYHEAD_BITRATE, playheadBitratePerType);
      }
      if (sequenceNumber != LONG_UNSET) {
        builder.put(CmcdV2Keys.KEY_SEQUENCE_NUMBER, sequenceNumber);
      }
      if (contentSignature != null) {
        builder.put(CmcdV2Keys.KEY_CONTENT_SIGNATURE, contentSignature);
      }
      if (targetBufferLengthPerType != null) {
        builder.put(CmcdV2Keys.KEY_TARGET_BUFFER_LENGTH, targetBufferLengthPerType);
      }

      return builder.buildOrThrow();
    }

    private ImmutableMap<String, Object> buildSessionKeys() {
      ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

      // cid (content ID)
      if (configuration.contentId != null) {
        builder.put(CmcdConfiguration.KEY_CONTENT_ID, configuration.contentId);
      }

      // sid (session ID)
      if (configuration.sessionId != null) {
        builder.put(CmcdConfiguration.KEY_SESSION_ID, configuration.sessionId);
      }

      // sf (streaming format)
      builder.put(CmcdConfiguration.KEY_STREAMING_FORMAT, streamingFormat);

      // st (stream type)
      if (streamType != null) {
        builder.put(CmcdConfiguration.KEY_STREAM_TYPE, streamType);
      }

      // pr (playback rate)
      if (playbackRate != C.RATE_UNSET && playbackRate != 1.0f) {
        builder.put(CmcdConfiguration.KEY_PLAYBACK_RATE, playbackRate);
      }

      // v=2 (always for v2 data)
      builder.put(CmcdConfiguration.KEY_VERSION, 2);

      // V2 Session keys
      if (mediaStartDelayMs != LONG_UNSET) {
        builder.put(CmcdV2Keys.KEY_MEDIA_START_DELAY, mediaStartDelayMs);
      }

      return builder.buildOrThrow();
    }

    private ImmutableMap<String, Object> buildStatusKeys() {
      ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

      // rtp (requested maximum throughput)
      if (requestedMaximumThroughputKbps != C.RATE_UNSET_INT) {
        builder.put(
            CmcdConfiguration.KEY_MAXIMUM_REQUESTED_BITRATE, requestedMaximumThroughputKbps);
      }

      // bs (buffer starvation)
      if (bufferStarvationSet && bufferStarvation) {
        builder.put(CmcdConfiguration.KEY_BUFFER_STARVATION, true);
      }

      // V2 Status keys
      if (playheadTimeMs != LONG_UNSET) {
        builder.put(CmcdV2Keys.KEY_PLAYHEAD_TIME, playheadTimeMs);
      }
      if (bufferStarvationCount != null) {
        builder.put(CmcdV2Keys.KEY_BUFFER_STARVATION_COUNT, bufferStarvationCount);
      }
      if (bufferStarvationDuration != null) {
        builder.put(CmcdV2Keys.KEY_BUFFER_STARVATION_DURATION, bufferStarvationDuration);
      }
      if (bufferStarvationDurationAccumulated != null) {
        builder.put(
            CmcdV2Keys.KEY_BUFFER_STARVATION_DURATION_ACCUMULATED,
            bufferStarvationDurationAccumulated);
      }
      if (nonRenderedSet && nonRendered) {
        builder.put(CmcdV2Keys.KEY_NON_RENDERED, true);
      }
      if (liveLatencyMs != LONG_UNSET) {
        builder.put(CmcdV2Keys.KEY_LIVE_LATENCY, liveLatencyMs);
      }
      if (backgroundedSet && backgrounded) {
        builder.put(CmcdV2Keys.KEY_BACKGROUNDED, true);
      }
      if (playerErrorCode != null) {
        builder.put(CmcdV2Keys.KEY_PLAYER_ERROR_CODE, playerErrorCode);
      }

      return builder.buildOrThrow();
    }

    private ImmutableMap<String, Object> buildEventOnlyKeys() {
      ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

      // e (event type)
      if (eventType != null) {
        builder.put(CmcdV2Keys.KEY_EVENT_TYPE, eventType);
      }

      // ts (timestamp)
      if (timestampMs != LONG_UNSET) {
        builder.put(CmcdV2Keys.KEY_TIMESTAMP, timestampMs);
      }

      // cmsdd (CMSD Dynamic Header)
      if (cmsdDynamicHeader != null) {
        builder.put(CmcdV2Keys.KEY_CMSD_DYNAMIC_HEADER, cmsdDynamicHeader);
      }

      // cmsds (CMSD Static Header)
      if (cmsdStaticHeader != null) {
        builder.put(CmcdV2Keys.KEY_CMSD_STATIC_HEADER, cmsdStaticHeader);
      }

      // rc (response code)
      if (responseCode != INT_UNSET) {
        builder.put(CmcdV2Keys.KEY_RESPONSE_CODE, responseCode);
      }

      // smrt (SMRT Data Header)
      if (smrtDataHeader != null) {
        builder.put(CmcdV2Keys.KEY_SMRT_DATA_HEADER, smrtDataHeader);
      }

      // ttfb (time to first byte)
      if (timeToFirstByteMs != LONG_UNSET) {
        builder.put(CmcdV2Keys.KEY_TIME_TO_FIRST_BYTE, timeToFirstByteMs);
      }

      // ttfbb (time to first body byte)
      if (timeToFirstBodyByteMs != LONG_UNSET) {
        builder.put(CmcdV2Keys.KEY_TIME_TO_FIRST_BODY_BYTE, timeToFirstBodyByteMs);
      }

      // ttlb (time to last byte)
      if (timeToLastByteMs != LONG_UNSET) {
        builder.put(CmcdV2Keys.KEY_TIME_TO_LAST_BYTE, timeToLastByteMs);
      }

      // url (requested URL)
      if (requestedUrl != null) {
        builder.put(CmcdV2Keys.KEY_REQUESTED_URL, requestedUrl);
      }

      // cen (custom event name)
      if (customEventName != null) {
        builder.put(CmcdV2Keys.KEY_CUSTOM_EVENT_NAME, customEventName);
      }

      // h (hostname)
      if (hostname != null) {
        builder.put(CmcdV2Keys.KEY_HOSTNAME, hostname);
      }

      return builder.buildOrThrow();
    }
  }
}
