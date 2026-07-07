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

import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.StringDef;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constants and type annotations for CMCD v2 (CTA-5004-B) reserved keys, event types, state types,
 * version modes, and header group assignments.
 *
 * <p>This class extends the key vocabulary defined in {@link CmcdConfiguration} with the new keys
 * introduced in the CTA-5004-B specification without modifying existing v1 classes.
 */
@UnstableApi
public final class CmcdV2Keys {

  private CmcdV2Keys() {}

  // =============================================================================================
  // Event Type annotation and constants
  // =============================================================================================

  /**
   * Event types for Event Mode reporting per CTA-5004-B.
   *
   * <p>One of {@link #EVENT_TYPE_AD_BREAK_START}, {@link #EVENT_TYPE_AD_BREAK_END}, {@link
   * #EVENT_TYPE_AD_END}, {@link #EVENT_TYPE_AD_START}, {@link #EVENT_TYPE_BACKGROUNDED}, {@link
   * #EVENT_TYPE_BITRATE_CHANGE}, {@link #EVENT_TYPE_CONTENT_ID_CHANGE}, {@link
   * #EVENT_TYPE_CUSTOM_EVENT}, {@link #EVENT_TYPE_ERROR}, {@link #EVENT_TYPE_HOSTNAME_CHANGE},
   * {@link #EVENT_TYPE_MUTE}, {@link #EVENT_TYPE_PLAYER_COLLAPSE}, {@link
   * #EVENT_TYPE_PLAYER_EXPAND}, {@link #EVENT_TYPE_PLAYBACK_RATE_CHANGE}, {@link
   * #EVENT_TYPE_PLAY_STATE_CHANGE}, {@link #EVENT_TYPE_RESPONSE_RECEIVED}, {@link
   * #EVENT_TYPE_SKIP}, {@link #EVENT_TYPE_TIME_INTERVAL}, or {@link #EVENT_TYPE_UNMUTE}.
   */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    EVENT_TYPE_AD_BREAK_START,
    EVENT_TYPE_AD_BREAK_END,
    EVENT_TYPE_AD_END,
    EVENT_TYPE_AD_START,
    EVENT_TYPE_BACKGROUNDED,
    EVENT_TYPE_BITRATE_CHANGE,
    EVENT_TYPE_CONTENT_ID_CHANGE,
    EVENT_TYPE_CUSTOM_EVENT,
    EVENT_TYPE_ERROR,
    EVENT_TYPE_HOSTNAME_CHANGE,
    EVENT_TYPE_MUTE,
    EVENT_TYPE_PLAYER_COLLAPSE,
    EVENT_TYPE_PLAYER_EXPAND,
    EVENT_TYPE_PLAYBACK_RATE_CHANGE,
    EVENT_TYPE_PLAY_STATE_CHANGE,
    EVENT_TYPE_RESPONSE_RECEIVED,
    EVENT_TYPE_SKIP,
    EVENT_TYPE_TIME_INTERVAL,
    EVENT_TYPE_UNMUTE
  })
  @Documented
  @Target(TYPE_USE)
  public @interface EventType {}

  /** Ad break start event type token. */
  public static final String EVENT_TYPE_AD_BREAK_START = "abs";

  /** Ad break end event type token. */
  public static final String EVENT_TYPE_AD_BREAK_END = "abe";

  /** Ad end event type token. */
  public static final String EVENT_TYPE_AD_END = "ae";

  /** Ad start event type token. */
  public static final String EVENT_TYPE_AD_START = "as";

  /** Backgrounded event type token. */
  public static final String EVENT_TYPE_BACKGROUNDED = "b";

  /** Bitrate change event type token. */
  public static final String EVENT_TYPE_BITRATE_CHANGE = "bc";

  /** Content ID change event type token. */
  public static final String EVENT_TYPE_CONTENT_ID_CHANGE = "c";

  /** Custom event type token. */
  public static final String EVENT_TYPE_CUSTOM_EVENT = "ce";

  /** Error event type token. */
  public static final String EVENT_TYPE_ERROR = "e";

  /** Hostname change event type token. */
  public static final String EVENT_TYPE_HOSTNAME_CHANGE = "h";

  /** Mute event type token. */
  public static final String EVENT_TYPE_MUTE = "m";

  /** Player collapse event type token. */
  public static final String EVENT_TYPE_PLAYER_COLLAPSE = "pc";

  /** Player expand event type token. */
  public static final String EVENT_TYPE_PLAYER_EXPAND = "pe";

  /** Playback rate change event type token. */
  public static final String EVENT_TYPE_PLAYBACK_RATE_CHANGE = "pr";

  /** Play state change event type token. */
  public static final String EVENT_TYPE_PLAY_STATE_CHANGE = "ps";

  /** Response received event type token. */
  public static final String EVENT_TYPE_RESPONSE_RECEIVED = "rr";

  /** Skip event type token. */
  public static final String EVENT_TYPE_SKIP = "sk";

  /** Time interval (heartbeat) event type token. */
  public static final String EVENT_TYPE_TIME_INTERVAL = "t";

  /** Unmute event type token. */
  public static final String EVENT_TYPE_UNMUTE = "um";

  // =============================================================================================
  // State Type annotation and constants
  // =============================================================================================

  /**
   * State type tokens for the {@code sta} key per CTA-5004-B.
   *
   * <p>One of {@link #STATE_TYPE_STARTING}, {@link #STATE_TYPE_PLAYING}, {@link
   * #STATE_TYPE_SEEKING}, {@link #STATE_TYPE_REBUFFERING}, {@link #STATE_TYPE_PAUSED}, {@link
   * #STATE_TYPE_ENDED}, {@link #STATE_TYPE_FATAL_ERROR}, {@link #STATE_TYPE_QUIT}, or {@link
   * #STATE_TYPE_PRELOADING}.
   */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    STATE_TYPE_STARTING,
    STATE_TYPE_PLAYING,
    STATE_TYPE_SEEKING,
    STATE_TYPE_REBUFFERING,
    STATE_TYPE_PAUSED,
    STATE_TYPE_ENDED,
    STATE_TYPE_FATAL_ERROR,
    STATE_TYPE_QUIT,
    STATE_TYPE_PRELOADING
  })
  @Documented
  @Target(TYPE_USE)
  public @interface StateType {}

  /** Starting state type token. */
  public static final String STATE_TYPE_STARTING = "s";

  /** Playing state type token. */
  public static final String STATE_TYPE_PLAYING = "p";

  /** Seeking state type token. */
  public static final String STATE_TYPE_SEEKING = "k";

  /** Rebuffering state type token. */
  public static final String STATE_TYPE_REBUFFERING = "r";

  /** Paused state type token. */
  public static final String STATE_TYPE_PAUSED = "a";

  /** Ended state type token. */
  public static final String STATE_TYPE_ENDED = "e";

  /** Fatal error state type token. */
  public static final String STATE_TYPE_FATAL_ERROR = "f";

  /** Quit state type token. */
  public static final String STATE_TYPE_QUIT = "q";

  /** Preloading state type token. */
  public static final String STATE_TYPE_PRELOADING = "d";

  // =============================================================================================
  // Version Mode annotation and constants
  // =============================================================================================

  /**
   * Version mode for mixed-version configurations.
   *
   * <p>One of {@link #VERSION_MODE_V2_ONLY} or {@link #VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT}.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({VERSION_MODE_V2_ONLY, VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT})
  @Documented
  @Target(TYPE_USE)
  public @interface VersionMode {}

  /** V2-only mode: all payloads use v2 syntax. */
  public static final int VERSION_MODE_V2_ONLY = 0;

  /** Mixed mode: Request Mode uses v1 syntax, Event Mode uses v2 syntax. */
  public static final int VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT = 1;

  // =============================================================================================
  // New CTA-5004-B reserved key constants
  // =============================================================================================

  /** Event type key ({@code e}). Event only (N.A). */
  public static final String KEY_EVENT_TYPE = "e";

  /** State key ({@code sta}). CMCD-Request. */
  public static final String KEY_STATE = "sta";

  /** Timestamp key ({@code ts}). Event only (N.A). */
  public static final String KEY_TIMESTAMP = "ts";

  /** Dropped frames accumulated key ({@code dfa}). CMCD-Request. */
  public static final String KEY_DROPPED_FRAMES_ACCUMULATED = "dfa";

  /** Live latency key ({@code ltc}). CMCD-Status. */
  public static final String KEY_LIVE_LATENCY = "ltc";

  /** Media start delay key ({@code msd}). CMCD-Session. */
  public static final String KEY_MEDIA_START_DELAY = "msd";

  /** Playhead bitrate key ({@code pb}). Inner list. CMCD-Request. */
  public static final String KEY_PLAYHEAD_BITRATE = "pb";

  /** Playhead time key ({@code pt}). CMCD-Status. */
  public static final String KEY_PLAYHEAD_TIME = "pt";

  /** Buffer starvation count key ({@code bsa}). Inner list. CMCD-Status. */
  public static final String KEY_BUFFER_STARVATION_COUNT = "bsa";

  /** Buffer starvation duration key ({@code bsd}). Inner list. CMCD-Status. */
  public static final String KEY_BUFFER_STARVATION_DURATION = "bsd";

  /** Buffer starvation duration accumulated key ({@code bsda}). Inner list. CMCD-Status. */
  public static final String KEY_BUFFER_STARVATION_DURATION_ACCUMULATED = "bsda";

  /** CMSD Dynamic Header key ({@code cmsdd}). Event only (N.A). */
  public static final String KEY_CMSD_DYNAMIC_HEADER = "cmsdd";

  /** CMSD Static Header key ({@code cmsds}). Event only (N.A). */
  public static final String KEY_CMSD_STATIC_HEADER = "cmsds";

  /** Response code key ({@code rc}). Event only (N.A). */
  public static final String KEY_RESPONSE_CODE = "rc";

  /** SMRT Data Header key ({@code smrt}). Event only (N.A). */
  public static final String KEY_SMRT_DATA_HEADER = "smrt";

  /** Time to first byte key ({@code ttfb}). Event only (N.A). */
  public static final String KEY_TIME_TO_FIRST_BYTE = "ttfb";

  /** Time to first body byte key ({@code ttfbb}). Event only (N.A). */
  public static final String KEY_TIME_TO_FIRST_BODY_BYTE = "ttfbb";

  /** Time to last byte key ({@code ttlb}). Event only (N.A). */
  public static final String KEY_TIME_TO_LAST_BYTE = "ttlb";

  /** Requested URL key ({@code url}). Event only (N.A). */
  public static final String KEY_REQUESTED_URL = "url";

  /** Custom event name key ({@code cen}). Event only (N.A). */
  public static final String KEY_CUSTOM_EVENT_NAME = "cen";

  /** Hostname key ({@code h}). Event only (N.A). */
  public static final String KEY_HOSTNAME = "h";

  /** Non rendered key ({@code nr}). CMCD-Status. */
  public static final String KEY_NON_RENDERED = "nr";

  /** Sequence number key ({@code sn}). CMCD-Request. */
  public static final String KEY_SEQUENCE_NUMBER = "sn";

  /** Content signature key ({@code cs}). CMCD-Request. */
  public static final String KEY_CONTENT_SIGNATURE = "cs";

  /** Lowest aggregated bitrate key ({@code lab}). Inner list. CMCD-Object. */
  public static final String KEY_LOWEST_AGGREGATED_BITRATE = "lab";

  /** Lowest encoded bitrate key ({@code lb}). Inner list. CMCD-Object. */
  public static final String KEY_LOWEST_ENCODED_BITRATE = "lb";

  /** Top aggregated bitrate key ({@code tab}). Inner list. CMCD-Object. */
  public static final String KEY_TOP_AGGREGATED_BITRATE = "tab";

  /** Target buffer length key ({@code tbl}). Inner list. CMCD-Request. */
  public static final String KEY_TARGET_BUFFER_LENGTH = "tbl";

  /** Top playable bitrate key ({@code tpb}). Inner list. CMCD-Object. */
  public static final String KEY_TOP_PLAYABLE_BITRATE = "tpb";

  /** Aggregate encoded bitrate key ({@code ab}). Inner list. CMCD-Object. */
  public static final String KEY_AGGREGATE_ENCODED_BITRATE = "ab";

  /** Backgrounded key ({@code bg}). CMCD-Status. */
  public static final String KEY_BACKGROUNDED = "bg";

  /** Player error code key ({@code ec}). Inner list. CMCD-Status. */
  public static final String KEY_PLAYER_ERROR_CODE = "ec";

  // =============================================================================================
  // New Stream Type and Streaming Format constants
  // =============================================================================================

  /** Represents the low-latency LIVE stream type. */
  public static final String STREAM_TYPE_LOW_LATENCY_LIVE = "ll";

  /** Represents the HESP streaming format. */
  public static final String STREAMING_FORMAT_HESP = "e";

  // =============================================================================================
  // Header group assignments for new v2 keys
  // =============================================================================================

  /**
   * Set of new v2 keys assigned to the CMCD-Object header group.
   *
   * <p>Contains: {@code lab}, {@code lb}, {@code tab}, {@code tpb}, {@code ab}.
   */
  public static final ImmutableSet<String> HEADER_GROUP_OBJECT_KEYS =
      ImmutableSet.of(
          KEY_LOWEST_AGGREGATED_BITRATE,
          KEY_LOWEST_ENCODED_BITRATE,
          KEY_TOP_AGGREGATED_BITRATE,
          KEY_TOP_PLAYABLE_BITRATE,
          KEY_AGGREGATE_ENCODED_BITRATE);

  /**
   * Set of new v2 keys assigned to the CMCD-Request header group.
   *
   * <p>Contains: {@code sta}, {@code dfa}, {@code pb}, {@code sn}, {@code cs}, {@code tbl}.
   */
  public static final ImmutableSet<String> HEADER_GROUP_REQUEST_KEYS =
      ImmutableSet.of(
          KEY_STATE,
          KEY_DROPPED_FRAMES_ACCUMULATED,
          KEY_PLAYHEAD_BITRATE,
          KEY_SEQUENCE_NUMBER,
          KEY_CONTENT_SIGNATURE,
          KEY_TARGET_BUFFER_LENGTH);

  /**
   * Set of new v2 keys assigned to the CMCD-Session header group.
   *
   * <p>Contains: {@code msd}.
   */
  public static final ImmutableSet<String> HEADER_GROUP_SESSION_KEYS =
      ImmutableSet.of(KEY_MEDIA_START_DELAY);

  /**
   * Set of new v2 keys assigned to the CMCD-Status header group.
   *
   * <p>Contains: {@code pt}, {@code bsa}, {@code bsd}, {@code bsda}, {@code nr}, {@code ltc},
   * {@code bg}, {@code ec}.
   */
  public static final ImmutableSet<String> HEADER_GROUP_STATUS_KEYS =
      ImmutableSet.of(
          KEY_PLAYHEAD_TIME,
          KEY_BUFFER_STARVATION_COUNT,
          KEY_BUFFER_STARVATION_DURATION,
          KEY_BUFFER_STARVATION_DURATION_ACCUMULATED,
          KEY_NON_RENDERED,
          KEY_LIVE_LATENCY,
          KEY_BACKGROUNDED,
          KEY_PLAYER_ERROR_CODE);

  /**
   * Set of keys that are only valid in Event Mode (not attached to request headers).
   *
   * <p>Contains: {@code e}, {@code ts}, {@code cmsdd}, {@code cmsds}, {@code rc}, {@code smrt},
   * {@code ttfb}, {@code ttfbb}, {@code ttlb}, {@code url}, {@code cen}, {@code h}.
   */
  public static final ImmutableSet<String> EVENT_ONLY_KEYS =
      new ImmutableSet.Builder<String>()
          .add(KEY_EVENT_TYPE)
          .add(KEY_TIMESTAMP)
          .add(KEY_CMSD_DYNAMIC_HEADER)
          .add(KEY_CMSD_STATIC_HEADER)
          .add(KEY_RESPONSE_CODE)
          .add(KEY_SMRT_DATA_HEADER)
          .add(KEY_TIME_TO_FIRST_BYTE)
          .add(KEY_TIME_TO_FIRST_BODY_BYTE)
          .add(KEY_TIME_TO_LAST_BYTE)
          .add(KEY_REQUESTED_URL)
          .add(KEY_CUSTOM_EVENT_NAME)
          .add(KEY_HOSTNAME)
          .build();

  /**
   * Maps each new v2 key to its assigned header group name.
   *
   * <p>Keys in this map are assigned to standard CMCD header groups for header-mode transmission.
   * Keys not present in this map (those in {@link #EVENT_ONLY_KEYS}) are only valid for Event Mode
   * reporting and are not attached to request headers.
   */
  public static final ImmutableMap<String, String> KEY_TO_HEADER_GROUP =
      new ImmutableMap.Builder<String, String>()
          // CMCD-Object keys
          .put(KEY_LOWEST_AGGREGATED_BITRATE, CmcdConfiguration.KEY_CMCD_OBJECT)
          .put(KEY_LOWEST_ENCODED_BITRATE, CmcdConfiguration.KEY_CMCD_OBJECT)
          .put(KEY_TOP_AGGREGATED_BITRATE, CmcdConfiguration.KEY_CMCD_OBJECT)
          .put(KEY_TOP_PLAYABLE_BITRATE, CmcdConfiguration.KEY_CMCD_OBJECT)
          .put(KEY_AGGREGATE_ENCODED_BITRATE, CmcdConfiguration.KEY_CMCD_OBJECT)
          // CMCD-Request keys
          .put(KEY_STATE, CmcdConfiguration.KEY_CMCD_REQUEST)
          .put(KEY_DROPPED_FRAMES_ACCUMULATED, CmcdConfiguration.KEY_CMCD_REQUEST)
          .put(KEY_PLAYHEAD_BITRATE, CmcdConfiguration.KEY_CMCD_REQUEST)
          .put(KEY_SEQUENCE_NUMBER, CmcdConfiguration.KEY_CMCD_REQUEST)
          .put(KEY_CONTENT_SIGNATURE, CmcdConfiguration.KEY_CMCD_REQUEST)
          .put(KEY_TARGET_BUFFER_LENGTH, CmcdConfiguration.KEY_CMCD_REQUEST)
          // CMCD-Session keys
          .put(KEY_MEDIA_START_DELAY, CmcdConfiguration.KEY_CMCD_SESSION)
          // CMCD-Status keys
          .put(KEY_PLAYHEAD_TIME, CmcdConfiguration.KEY_CMCD_STATUS)
          .put(KEY_BUFFER_STARVATION_COUNT, CmcdConfiguration.KEY_CMCD_STATUS)
          .put(KEY_BUFFER_STARVATION_DURATION, CmcdConfiguration.KEY_CMCD_STATUS)
          .put(KEY_BUFFER_STARVATION_DURATION_ACCUMULATED, CmcdConfiguration.KEY_CMCD_STATUS)
          .put(KEY_NON_RENDERED, CmcdConfiguration.KEY_CMCD_STATUS)
          .put(KEY_LIVE_LATENCY, CmcdConfiguration.KEY_CMCD_STATUS)
          .put(KEY_BACKGROUNDED, CmcdConfiguration.KEY_CMCD_STATUS)
          .put(KEY_PLAYER_ERROR_CODE, CmcdConfiguration.KEY_CMCD_STATUS)
          .buildOrThrow();
}
