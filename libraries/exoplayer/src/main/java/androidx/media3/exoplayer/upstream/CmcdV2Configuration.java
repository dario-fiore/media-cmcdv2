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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Configuration for CMCD v2 (CTA-5004-B) reporting.
 *
 * <p>Extends {@link CmcdConfiguration} with v2-specific capabilities: Event Mode targets, body
 * transmission, mixed-version mode, and extended content ID length.
 *
 * <p>Use the {@link Builder} to construct instances.
 */
@UnstableApi
public final class CmcdV2Configuration extends CmcdConfiguration {

  /** Maximum content ID length for v2 (128 characters). */
  public static final int MAX_CONTENT_ID_LENGTH_V2 = 128;

  /** Body transmission mode for HTTP POST body format. Only valid for Event Mode. */
  public static final int MODE_BODY = 2;

  /** Default heartbeat interval in milliseconds (30 seconds per CTA-5004-B). */
  public static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30_000;

  /**
   * The version mode for this configuration.
   *
   * <p>One of {@link CmcdV2Keys#VERSION_MODE_V2_ONLY} or {@link
   * CmcdV2Keys#VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT}.
   */
  @CmcdV2Keys.VersionMode public final int versionMode;

  /**
   * Event targets for Event Mode reporting. Empty if Event Mode is disabled.
   *
   * <p>Each target defines an endpoint URL and optional filtering criteria.
   */
  public final ImmutableList<EventTarget> eventTargets;

  /**
   * Heartbeat interval in milliseconds. {@code 0} disables heartbeat.
   *
   * <p>Default is 30 seconds (30000 ms) per CTA-5004-B specification.
   */
  public final long heartbeatIntervalMs;

  /**
   * Whether Request Mode payloads use v1 syntax in mixed mode.
   *
   * <p>This is {@code true} when {@link #versionMode} is {@link
   * CmcdV2Keys#VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT}, meaning Request Mode uses v1 syntax while
   * Event Mode uses v2 syntax.
   */
  public final boolean useV1SyntaxForRequestMode;

  private CmcdV2Configuration(
      @Nullable String sessionId,
      @Nullable String contentId,
      RequestConfig requestConfig,
      @DataTransmissionMode int dataTransmissionMode,
      @CmcdV2Keys.VersionMode int versionMode,
      ImmutableList<EventTarget> eventTargets,
      long heartbeatIntervalMs,
      boolean useV1SyntaxForRequestMode) {
    super(sessionId, contentId, requestConfig, dataTransmissionMode, MAX_CONTENT_ID_LENGTH_V2);
    this.versionMode = versionMode;
    this.eventTargets = eventTargets;
    this.heartbeatIntervalMs = heartbeatIntervalMs;
    this.useV1SyntaxForRequestMode = useV1SyntaxForRequestMode;
  }

  /** Builder for constructing {@link CmcdV2Configuration} instances. */
  public static final class Builder {

    @Nullable private String sessionId;
    @Nullable private String contentId;
    private RequestConfig requestConfig;
    private @DataTransmissionMode int dataTransmissionMode;
    private @CmcdV2Keys.VersionMode int versionMode;
    private ImmutableList<EventTarget> eventTargets;
    private long heartbeatIntervalMs;

    /** Creates a new {@link Builder} with default values. */
    public Builder() {
      this.requestConfig = new RequestConfig() {};
      this.dataTransmissionMode = MODE_REQUEST_HEADER;
      this.versionMode = CmcdV2Keys.VERSION_MODE_V2_ONLY;
      this.eventTargets = ImmutableList.of();
      this.heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS;
    }

    /**
     * Sets the session ID.
     *
     * <p>Maximum length is 64 characters.
     *
     * @param sessionId The session ID, or {@code null} to unset.
     * @return This builder, for convenience.
     * @throws IllegalArgumentException If {@code sessionId} exceeds 64 characters.
     */
    @CanIgnoreReturnValue
    public Builder setSessionId(@Nullable String sessionId) {
      checkArgument(sessionId == null || sessionId.length() <= MAX_ID_LENGTH);
      this.sessionId = sessionId;
      return this;
    }

    /**
     * Sets the content ID.
     *
     * <p>Maximum length is 128 characters for v2 configurations.
     *
     * @param contentId The content ID, or {@code null} to unset.
     * @return This builder, for convenience.
     * @throws IllegalArgumentException If {@code contentId} exceeds 128 characters.
     */
    @CanIgnoreReturnValue
    public Builder setContentId(@Nullable String contentId) {
      checkArgument(
          contentId == null || contentId.length() <= MAX_CONTENT_ID_LENGTH_V2,
          "Content ID length must not exceed %s characters, but was %s",
          MAX_CONTENT_ID_LENGTH_V2,
          contentId == null ? 0 : contentId.length());
      this.contentId = contentId;
      return this;
    }

    /**
     * Sets the request configuration.
     *
     * @param requestConfig The request configuration.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setRequestConfig(RequestConfig requestConfig) {
      this.requestConfig = checkNotNull(requestConfig);
      return this;
    }

    /**
     * Sets the data transmission mode for Request Mode payloads.
     *
     * <p>Default is {@link CmcdConfiguration#MODE_REQUEST_HEADER}.
     *
     * @param dataTransmissionMode The transmission mode.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setDataTransmissionMode(@DataTransmissionMode int dataTransmissionMode) {
      this.dataTransmissionMode = dataTransmissionMode;
      return this;
    }

    /**
     * Sets the version mode.
     *
     * <p>Default is {@link CmcdV2Keys#VERSION_MODE_V2_ONLY}.
     *
     * @param versionMode The version mode.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setVersionMode(@CmcdV2Keys.VersionMode int versionMode) {
      this.versionMode = versionMode;
      return this;
    }

    /**
     * Sets the event targets for Event Mode reporting.
     *
     * <p>Default is an empty list (Event Mode disabled).
     *
     * @param eventTargets The list of event targets.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setEventTargets(ImmutableList<EventTarget> eventTargets) {
      this.eventTargets = checkNotNull(eventTargets);
      return this;
    }

    /**
     * Sets the heartbeat interval in milliseconds.
     *
     * <p>Default is 30 seconds (30000 ms) per the CTA-5004-B specification. Set to {@code 0} to
     * disable heartbeat.
     *
     * @param heartbeatIntervalMs The heartbeat interval in milliseconds.
     * @return This builder, for convenience.
     * @throws IllegalArgumentException If {@code heartbeatIntervalMs} is negative.
     */
    @CanIgnoreReturnValue
    public Builder setHeartbeatIntervalMs(long heartbeatIntervalMs) {
      checkArgument(heartbeatIntervalMs >= 0, "Heartbeat interval must not be negative.");
      this.heartbeatIntervalMs = heartbeatIntervalMs;
      return this;
    }

    /**
     * Builds the {@link CmcdV2Configuration}.
     *
     * <p>The {@link CmcdV2Configuration#useV1SyntaxForRequestMode} field is automatically set to
     * {@code true} when the version mode is {@link
     * CmcdV2Keys#VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT}.
     *
     * @return A new immutable {@link CmcdV2Configuration} instance.
     */
    public CmcdV2Configuration build() {
      boolean useV1SyntaxForRequestMode =
          versionMode == CmcdV2Keys.VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT;
      return new CmcdV2Configuration(
          sessionId,
          contentId,
          requestConfig,
          dataTransmissionMode,
          versionMode,
          eventTargets,
          heartbeatIntervalMs,
          useV1SyntaxForRequestMode);
    }
  }
}
