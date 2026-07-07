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
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Configuration for a single Event Mode reporting destination.
 *
 * <p>An {@code EventTarget} defines the endpoint URL, optional access token for authentication, and
 * optional filters for which CMCD keys and event types are sent to this target.
 *
 * <p>Use the {@link Builder} to construct instances.
 */
@UnstableApi
public final class EventTarget {

  /** The endpoint URL to POST CMCD reports to. */
  public final String url;

  /**
   * Access token for the Authorization header, or {@code null} if unauthenticated.
   *
   * <p>When set, the token is included in the HTTP Authorization header of POST requests to this
   * target.
   */
  @Nullable public final String accessToken;

  /**
   * Allowed CMCD keys for this target. Empty set means all keys are allowed.
   *
   * <p>When non-empty, only keys in this set are included in reports sent to this target.
   */
  public final ImmutableSet<String> allowedKeys;

  /**
   * Allowed event types for this target. Empty set means all events are allowed.
   *
   * <p>When non-empty, reports are only sent to this target when the event type is in this set.
   */
  public final ImmutableSet<String> allowedEventTypes;

  private EventTarget(
      String url,
      @Nullable String accessToken,
      ImmutableSet<String> allowedKeys,
      ImmutableSet<String> allowedEventTypes) {
    this.url = url;
    this.accessToken = accessToken;
    this.allowedKeys = allowedKeys;
    this.allowedEventTypes = allowedEventTypes;
  }

  /** Builder for constructing {@link EventTarget} instances. */
  public static final class Builder {

    private String url;
    @Nullable private String accessToken;
    private ImmutableSet<String> allowedKeys;
    private ImmutableSet<String> allowedEventTypes;

    /**
     * Creates a new {@link Builder} with the specified endpoint URL.
     *
     * @param url The endpoint URL to POST CMCD reports to. Must not be empty.
     * @throws NullPointerException If {@code url} is null.
     * @throws IllegalArgumentException If {@code url} is empty.
     */
    public Builder(String url) {
      checkNotNull(url);
      checkArgument(!url.isEmpty(), "EventTarget URL must not be empty.");
      this.url = url;
      this.accessToken = null;
      this.allowedKeys = ImmutableSet.of();
      this.allowedEventTypes = ImmutableSet.of();
    }

    /**
     * Sets the access token for the Authorization header.
     *
     * <p>Default is {@code null} (unauthenticated).
     *
     * @param accessToken The access token, or {@code null} to send requests without an
     *     Authorization header.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setAccessToken(@Nullable String accessToken) {
      this.accessToken = accessToken;
      return this;
    }

    /**
     * Sets the allowed CMCD keys for this target.
     *
     * <p>When the set is non-empty, only keys in this set are included in reports sent to this
     * target. An empty set means all keys are allowed.
     *
     * <p>Default is an empty set (all keys allowed).
     *
     * @param allowedKeys The set of allowed CMCD keys.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setAllowedKeys(ImmutableSet<String> allowedKeys) {
      this.allowedKeys = checkNotNull(allowedKeys);
      return this;
    }

    /**
     * Sets the allowed event types for this target.
     *
     * <p>When the set is non-empty, reports are only sent to this target when the event type is in
     * this set. An empty set means all event types are allowed.
     *
     * <p>Default is an empty set (all event types allowed).
     *
     * @param allowedEventTypes The set of allowed event types.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setAllowedEventTypes(ImmutableSet<String> allowedEventTypes) {
      this.allowedEventTypes = checkNotNull(allowedEventTypes);
      return this;
    }

    /**
     * Builds the {@link EventTarget}.
     *
     * @return A new immutable {@link EventTarget} instance.
     */
    public EventTarget build() {
      return new EventTarget(url, accessToken, allowedKeys, allowedEventTypes);
    }
  }
}
