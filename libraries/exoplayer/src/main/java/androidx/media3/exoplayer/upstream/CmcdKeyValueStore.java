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

import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableMap;

/**
 * Interface exposing CMCD key-value pairs grouped by header group.
 *
 * <p>This enables both v1 {@link CmcdData} and v2 CmcdV2Data to be serialized through a common
 * {@link CmcdSerializer} without tight coupling to either implementation.
 *
 * <p>Values in the maps may be of type {@link Integer}, {@link Long}, {@link Float}, {@link
 * Double}, {@link Boolean}, {@link String}, or {@link InnerListValue}.
 */
@UnstableApi
public interface CmcdKeyValueStore {

  /**
   * Returns the key-value pairs assigned to the CMCD-Object header group.
   *
   * <p>Keys whose values vary with the object being requested (e.g., {@code br}, {@code tb},
   * {@code d}, {@code ot}, {@code lab}, {@code lb}, {@code tab}, {@code tpb}, {@code ab}).
   */
  ImmutableMap<String, Object> getObjectKeys();

  /**
   * Returns the key-value pairs assigned to the CMCD-Request header group.
   *
   * <p>Keys whose values vary with each request (e.g., {@code bl}, {@code mtp}, {@code dl}, {@code
   * su}, {@code nor}, {@code nrr}, {@code sta}, {@code dfa}, {@code pb}, {@code sn}, {@code cs},
   * {@code tbl}).
   */
  ImmutableMap<String, Object> getRequestKeys();

  /**
   * Returns the key-value pairs assigned to the CMCD-Session header group.
   *
   * <p>Keys whose values are expected to be invariant over the life of the session (e.g., {@code
   * cid}, {@code sid}, {@code sf}, {@code st}, {@code pr}, {@code v}, {@code msd}).
   */
  ImmutableMap<String, Object> getSessionKeys();

  /**
   * Returns the key-value pairs assigned to the CMCD-Status header group.
   *
   * <p>Keys whose values do not vary with every request or object (e.g., {@code rtp}, {@code bs},
   * {@code pt}, {@code bsa}, {@code bsd}, {@code bsda}, {@code nr}, {@code ltc}, {@code bg},
   * {@code ec}).
   */
  ImmutableMap<String, Object> getStatusKeys();

  /**
   * Returns the key-value pairs that are only valid in Event Mode (body transmission).
   *
   * <p>These keys are not attached to request headers and are only included in body-mode output
   * (e.g., {@code e}, {@code ts}, {@code cmsdd}, {@code cmsds}, {@code rc}, {@code smrt}, {@code
   * ttfb}, {@code ttfbb}, {@code ttlb}, {@code url}, {@code cen}, {@code h}).
   *
   * <p>Implementations that do not support Event Mode may return an empty map.
   */
  ImmutableMap<String, Object> getEventOnlyKeys();
}
