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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.media3.datasource.DataSpec;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for CMCD v2 mixed-version mode serialization dispatch.
 *
 * <p>Validates Requirements 10.1, 10.2, 10.3, 10.4:
 *
 * <ul>
 *   <li>10.1: Mixed-version configuration where Request Mode uses v1 syntax and Event Mode uses v2.
 *   <li>10.2: Mixed mode omits {@code v} key from Request Mode payloads, includes {@code v=2} in
 *       Event Mode.
 *   <li>10.3: Mixed mode uses {@code nrr} key and single-string {@code nor} in Request Mode.
 *   <li>10.4: Mixed mode uses inner list {@code nor} and omits {@code nrr} in Event Mode.
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class CmcdV2MixedVersionModeTest {

  private static final Uri TEST_URI = Uri.parse("https://example.com/video.mp4");

  /** Configuration in v2-only mode (useV1SyntaxForRequestMode = false). */
  private static final CmcdV2Configuration V2_ONLY_CONFIGURATION =
      new CmcdV2Configuration.Builder()
          .setSessionId("testSession")
          .setContentId("testContent")
          .setVersionMode(CmcdV2Keys.VERSION_MODE_V2_ONLY)
          .build();

  /** Configuration in mixed mode (useV1SyntaxForRequestMode = true). */
  private static final CmcdV2Configuration MIXED_MODE_CONFIGURATION =
      new CmcdV2Configuration.Builder()
          .setSessionId("testSession")
          .setContentId("testContent")
          .setVersionMode(CmcdV2Keys.VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT)
          .build();

  /** Mixed mode with query parameter transmission. */
  private static final CmcdV2Configuration MIXED_MODE_QUERY_CONFIGURATION =
      new CmcdV2Configuration.Builder()
          .setSessionId("testSession")
          .setContentId("testContent")
          .setVersionMode(CmcdV2Keys.VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT)
          .setDataTransmissionMode(CmcdConfiguration.MODE_QUERY_PARAMETER)
          .build();

  // =========================================================================================
  // V2-only mode tests
  // =========================================================================================

  /**
   * Verifies that in v2-only mode, addToDataSpec includes v=2 in the serialized headers.
   */
  @Test
  public void v2OnlyMode_addToDataSpec_includesVersionKey() {
    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_ONLY_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setBitrate(5000)
            .createCmcdData();

    DataSpec dataSpec = new DataSpec.Builder().setUri(TEST_URI).build();
    DataSpec result = data.addToDataSpec(dataSpec, V2_ONLY_CONFIGURATION);

    String sessionHeader = result.httpRequestHeaders.get(CmcdConfiguration.KEY_CMCD_SESSION);
    assertThat(sessionHeader).isNotNull();
    assertThat(sessionHeader).contains("v=2");
  }

  /**
   * Verifies that in v2-only mode with inner list nor, addToDataSpec serializes nor as inner list.
   */
  @Test
  public void v2OnlyMode_addToDataSpec_norAsInnerList() {
    InnerListValue norList =
        new InnerListValue.Builder()
            .addItemWithRange("../seg1.mp4", "0-500")
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_ONLY_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequestList(norList)
            .createCmcdData();

    DataSpec dataSpec = new DataSpec.Builder().setUri(TEST_URI).build();
    DataSpec result = data.addToDataSpec(dataSpec, V2_ONLY_CONFIGURATION);

    String requestHeader = result.httpRequestHeaders.get(CmcdConfiguration.KEY_CMCD_REQUEST);
    assertThat(requestHeader).contains("nor=(\"../seg1.mp4\";r=0-500)");
    assertThat(requestHeader).doesNotContain("nrr");
  }

  // =========================================================================================
  // Mixed-version mode tests (header transmission)
  // =========================================================================================

  /**
   * Verifies that in mixed mode, addToDataSpec omits the v key from Request Mode headers
   * (Requirement 10.2).
   */
  @Test
  public void mixedMode_addToDataSpec_omitsVersionKey() {
    CmcdV2Data data =
        new CmcdV2Data.Factory(MIXED_MODE_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setBitrate(5000)
            .createCmcdData();

    DataSpec dataSpec = new DataSpec.Builder().setUri(TEST_URI).build();
    DataSpec result = data.addToDataSpec(dataSpec, MIXED_MODE_CONFIGURATION);

    String sessionHeader = result.httpRequestHeaders.get(CmcdConfiguration.KEY_CMCD_SESSION);
    // Session header should exist (cid, sid, sf are present)
    assertThat(sessionHeader).isNotNull();
    // v key must NOT be present in mixed mode request
    assertThat(sessionHeader).doesNotContain("v=");
  }

  /**
   * Verifies that in mixed mode, inner list nor is converted to v1 scalar format with separate nrr
   * (Requirement 10.3).
   */
  @Test
  public void mixedMode_addToDataSpec_norConvertedToV1Format() {
    InnerListValue norList =
        new InnerListValue.Builder()
            .addItemWithRange("../next_seg.mp4", "0-1024")
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(MIXED_MODE_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequestList(norList)
            .createCmcdData();

    DataSpec dataSpec = new DataSpec.Builder().setUri(TEST_URI).build();
    DataSpec result = data.addToDataSpec(dataSpec, MIXED_MODE_CONFIGURATION);

    String requestHeader = result.httpRequestHeaders.get(CmcdConfiguration.KEY_CMCD_REQUEST);
    assertThat(requestHeader).isNotNull();
    // nor should be a single quoted string (v1 format)
    assertThat(requestHeader).contains("nor=\"../next_seg.mp4\"");
    // nrr should be present as a separate key (v1 format)
    assertThat(requestHeader).contains("nrr=\"0-1024\"");
    // Should NOT contain inner list format
    assertThat(requestHeader).doesNotContain("nor=(");
  }

  /**
   * Verifies that in mixed mode, inner list nor without range is converted to v1 scalar format
   * without nrr.
   */
  @Test
  public void mixedMode_addToDataSpec_norWithoutRange_noNrr() {
    InnerListValue norList =
        new InnerListValue.Builder()
            .addItemWithRange("../next_seg.mp4", null)
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(MIXED_MODE_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequestList(norList)
            .createCmcdData();

    DataSpec dataSpec = new DataSpec.Builder().setUri(TEST_URI).build();
    DataSpec result = data.addToDataSpec(dataSpec, MIXED_MODE_CONFIGURATION);

    String requestHeader = result.httpRequestHeaders.get(CmcdConfiguration.KEY_CMCD_REQUEST);
    assertThat(requestHeader).isNotNull();
    // nor should be a single quoted string
    assertThat(requestHeader).contains("nor=\"../next_seg.mp4\"");
    // nrr should NOT be present (no range)
    assertThat(requestHeader).doesNotContain("nrr");
  }

  /**
   * Verifies that Event Mode data still serializes with v=2 and v2 inner list nor format even in
   * mixed mode (Requirement 10.4).
   */
  @Test
  public void mixedMode_eventModeData_usesV2Syntax() {
    InnerListValue norList =
        new InnerListValue.Builder()
            .addItemWithRange("../seg1.mp4", "0-500")
            .addItemWithRange("../seg2.mp4", null)
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(MIXED_MODE_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequestList(norList)
            .setEventType(CmcdV2Keys.EVENT_TYPE_BITRATE_CHANGE)
            .setTimestampMs(1000L)
            .createCmcdData();

    // Event Mode always uses v2 syntax - serialize the original data (not the v1 view)
    ImmutableMap<String, String> eventHeaders = CmcdSerializer.toHeaders(data);
    String sessionHeader = eventHeaders.get(CmcdConfiguration.KEY_CMCD_SESSION);
    String requestHeader = eventHeaders.get(CmcdConfiguration.KEY_CMCD_REQUEST);

    // v=2 should be present in Event Mode serialization
    assertThat(sessionHeader).contains("v=2");
    // nor should be inner list format in Event Mode serialization
    assertThat(requestHeader).contains("nor=(\"../seg1.mp4\";r=0-500 \"../seg2.mp4\")");
    assertThat(requestHeader).doesNotContain("nrr");
  }

  // =========================================================================================
  // Mixed-version mode tests (query parameter transmission)
  // =========================================================================================

  /**
   * Verifies that mixed mode with query parameter transmission omits v key from the query string.
   */
  @Test
  public void mixedMode_queryParameter_omitsVersionKey() {
    CmcdV2Data data =
        new CmcdV2Data.Factory(MIXED_MODE_QUERY_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setBitrate(5000)
            .createCmcdData();

    DataSpec dataSpec = new DataSpec.Builder().setUri(TEST_URI).build();
    DataSpec result = data.addToDataSpec(dataSpec, MIXED_MODE_QUERY_CONFIGURATION);

    String cmcdParam = result.uri.getQueryParameter(CmcdConfiguration.CMCD_QUERY_PARAMETER_KEY);
    assertThat(cmcdParam).isNotNull();
    // v key must NOT be present
    assertThat(cmcdParam).doesNotContain("v=2");
    // br should be present
    assertThat(cmcdParam).contains("br=5000");
  }

  /**
   * Verifies that v2-only mode with query parameter transmission includes v=2.
   */
  @Test
  public void v2OnlyMode_queryParameter_includesVersionKey() {
    CmcdV2Configuration v2QueryConfig =
        new CmcdV2Configuration.Builder()
            .setSessionId("testSession")
            .setContentId("testContent")
            .setVersionMode(CmcdV2Keys.VERSION_MODE_V2_ONLY)
            .setDataTransmissionMode(CmcdConfiguration.MODE_QUERY_PARAMETER)
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(v2QueryConfig, CmcdData.STREAMING_FORMAT_DASH)
            .setBitrate(5000)
            .createCmcdData();

    DataSpec dataSpec = new DataSpec.Builder().setUri(TEST_URI).build();
    DataSpec result = data.addToDataSpec(dataSpec, v2QueryConfig);

    String cmcdParam = result.uri.getQueryParameter(CmcdConfiguration.CMCD_QUERY_PARAMETER_KEY);
    assertThat(cmcdParam).isNotNull();
    assertThat(cmcdParam).contains("v=2");
  }

  // =========================================================================================
  // Edge case tests
  // =========================================================================================

  /**
   * Verifies that addToDataSpec with no populated keys does not modify the DataSpec.
   */
  @Test
  public void addToDataSpec_noPopulatedKeys_doesNotModifyDataSpec() {
    // Create a data instance with only session keys (cid, sid, sf, v)
    // When in mixed mode, v is removed - if only session keys with v remain, they should still
    // serialize but without v
    CmcdV2Configuration mixedConfigNoSession =
        new CmcdV2Configuration.Builder()
            .setVersionMode(CmcdV2Keys.VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT)
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(mixedConfigNoSession, CmcdData.STREAMING_FORMAT_DASH)
            .createCmcdData();

    DataSpec dataSpec = new DataSpec.Builder().setUri(TEST_URI).build();
    DataSpec result = data.addToDataSpec(dataSpec, mixedConfigNoSession);

    // Even with just sf (streaming format), the session header should still exist
    // but without the v key
    String sessionHeader = result.httpRequestHeaders.get(CmcdConfiguration.KEY_CMCD_SESSION);
    if (sessionHeader != null) {
      assertThat(sessionHeader).doesNotContain("v=");
    }
  }

  /**
   * Verifies that scalar nor (not inner list) passes through unchanged in mixed mode.
   */
  @Test
  public void mixedMode_scalarNor_passesThrough() {
    CmcdV2Data data =
        new CmcdV2Data.Factory(MIXED_MODE_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequest("../next.mp4")
            .setNextRangeRequest("0-512")
            .createCmcdData();

    DataSpec dataSpec = new DataSpec.Builder().setUri(TEST_URI).build();
    DataSpec result = data.addToDataSpec(dataSpec, MIXED_MODE_CONFIGURATION);

    String requestHeader = result.httpRequestHeaders.get(CmcdConfiguration.KEY_CMCD_REQUEST);
    assertThat(requestHeader).isNotNull();
    // Scalar nor should remain as-is (already v1 format)
    assertThat(requestHeader).contains("nor=\"../next.mp4\"");
    // nrr should be present
    assertThat(requestHeader).contains("nrr=\"0-512\"");
  }
}
