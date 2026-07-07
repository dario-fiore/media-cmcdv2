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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for CMCD v2 {@code nor} key formatting and {@code nrr} key removal behavior.
 *
 * <p>Validates Requirements 6.1, 6.2, 6.3, 6.4:
 *
 * <ul>
 *   <li>6.1: V2 mode formats {@code nor} as an inner list of strings.
 *   <li>6.2: Range information is expressed as a parameter within the {@code nor} inner list entry.
 *   <li>6.3: V1 mode emits {@code nor} as a single quoted string and {@code nrr} as a separate
 *       key.
 *   <li>6.4: {@code nrr} is omitted entirely from v2 payloads.
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class CmcdV2NorNrrFormatTest {

  private static final CmcdV2Configuration V2_CONFIGURATION =
      new CmcdV2Configuration.Builder()
          .setSessionId("testSession")
          .setContentId("testContent")
          .build();

  /**
   * Verifies that when {@code nextObjectRequestList} (InnerListValue) is set, the {@code nor} key
   * is formatted as an RFC 8941 inner list of strings (Requirement 6.1).
   */
  @Test
  public void v2Mode_norFormattedAsInnerList() {
    InnerListValue norList =
        new InnerListValue.Builder()
            .addItemWithRange("../next_segment.mp4", null)
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequestList(norList)
            .createCmcdData();

    ImmutableMap<String, Object> requestKeys = data.getRequestKeys();
    assertThat(requestKeys).containsKey(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST);

    Object norValue = requestKeys.get(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST);
    assertThat(norValue).isInstanceOf(InnerListValue.class);

    // Verify serialization produces inner list syntax
    String formatted =
        CmcdSerializer.formatValue(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST, norValue);
    assertThat(formatted).isEqualTo("nor=(\"../next_segment.mp4\")");
  }

  /**
   * Verifies that range information is expressed as a parameter (;r=range) within the {@code nor}
   * inner list entry (Requirement 6.2).
   */
  @Test
  public void v2Mode_norWithRange_rangeExpressedAsItemParameter() {
    InnerListValue norList =
        new InnerListValue.Builder()
            .addItemWithRange("../next_segment.mp4", "0-1024")
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequestList(norList)
            .createCmcdData();

    ImmutableMap<String, Object> requestKeys = data.getRequestKeys();
    Object norValue = requestKeys.get(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST);

    // Verify range is formatted as ;r=range parameter
    String formatted =
        CmcdSerializer.formatValue(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST, norValue);
    assertThat(formatted).isEqualTo("nor=(\"../next_segment.mp4\";r=0-1024)");
  }

  /**
   * Verifies that when inner list nor is used in v2, multiple URLs with ranges are formatted
   * correctly.
   */
  @Test
  public void v2Mode_norWithMultipleUrlsAndRanges() {
    InnerListValue norList =
        new InnerListValue.Builder()
            .addItemWithRange("../seg1.mp4", "0-500")
            .addItemWithRange("../seg2.mp4", "501-1000")
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequestList(norList)
            .createCmcdData();

    Object norValue = data.getRequestKeys().get(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST);

    String formatted =
        CmcdSerializer.formatValue(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST, norValue);
    assertThat(formatted)
        .isEqualTo("nor=(\"../seg1.mp4\";r=0-500 \"../seg2.mp4\";r=501-1000)");
  }

  /**
   * Verifies that the {@code nrr} key is omitted entirely from v2 payloads when inner list nor is
   * used (Requirement 6.4).
   */
  @Test
  public void v2Mode_nrrOmittedWhenInnerListNorUsed() {
    InnerListValue norList =
        new InnerListValue.Builder()
            .addItemWithRange("../next_segment.mp4", "0-1024")
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequestList(norList)
            .setNextRangeRequest("0-1024") // Set nrr as well, but it should be suppressed
            .createCmcdData();

    ImmutableMap<String, Object> requestKeys = data.getRequestKeys();
    assertThat(requestKeys).doesNotContainKey(CmcdConfiguration.KEY_NEXT_RANGE_REQUEST);
  }

  /**
   * Verifies that when only scalar {@code nextObjectRequest} is set (v1 path), {@code nor} is
   * formatted as a single double-quoted string (Requirement 6.3).
   */
  @Test
  public void v1Mode_norFormattedAsSingleQuotedString() {
    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequest("../next_segment.mp4")
            .createCmcdData();

    ImmutableMap<String, Object> requestKeys = data.getRequestKeys();
    Object norValue = requestKeys.get(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST);
    assertThat(norValue).isInstanceOf(String.class);

    // Verify serialization produces double-quoted string format (v1 style)
    String formatted =
        CmcdSerializer.formatValue(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST, norValue);
    assertThat(formatted).isEqualTo("nor=\"../next_segment.mp4\"");
  }

  /**
   * Verifies that when only scalar nor is set, {@code nrr} is included as a separate key
   * (Requirement 6.3).
   */
  @Test
  public void v1Mode_nrrIncludedAsSeparateKey() {
    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequest("../next_segment.mp4")
            .setNextRangeRequest("0-1024")
            .createCmcdData();

    ImmutableMap<String, Object> requestKeys = data.getRequestKeys();
    assertThat(requestKeys).containsKey(CmcdConfiguration.KEY_NEXT_RANGE_REQUEST);

    Object nrrValue = requestKeys.get(CmcdConfiguration.KEY_NEXT_RANGE_REQUEST);
    assertThat(nrrValue).isInstanceOf(String.class);

    // Verify nrr serialization produces double-quoted string
    String formatted =
        CmcdSerializer.formatValue(CmcdConfiguration.KEY_NEXT_RANGE_REQUEST, nrrValue);
    assertThat(formatted).isEqualTo("nrr=\"0-1024\"");
  }

  /**
   * Verifies that the inner list nor takes priority over scalar nor when both are set.
   */
  @Test
  public void v2Mode_innerListNorTakesPriorityOverScalarNor() {
    InnerListValue norList =
        new InnerListValue.Builder()
            .addItemWithRange("../v2_segment.mp4", null)
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequest("../v1_segment.mp4") // scalar (v1)
            .setNextObjectRequestList(norList) // inner list (v2) - takes priority
            .createCmcdData();

    ImmutableMap<String, Object> requestKeys = data.getRequestKeys();
    Object norValue = requestKeys.get(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST);

    // Inner list should win
    assertThat(norValue).isInstanceOf(InnerListValue.class);
    String formatted =
        CmcdSerializer.formatValue(CmcdConfiguration.KEY_NEXT_OBJECT_REQUEST, norValue);
    assertThat(formatted).isEqualTo("nor=(\"../v2_segment.mp4\")");
  }

  /**
   * Verifies end-to-end header serialization with v2 nor (inner list) produces correct output and
   * omits nrr.
   */
  @Test
  public void v2Mode_headerSerialization_norInnerListAndNoNrr() {
    InnerListValue norList =
        new InnerListValue.Builder()
            .addItemWithRange("../seg.mp4", "0-500")
            .build();

    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequestList(norList)
            .setNextRangeRequest("0-500") // Should be suppressed in v2
            .createCmcdData();

    ImmutableMap<String, String> headers = CmcdSerializer.toHeaders(data);
    String requestHeader = headers.get(CmcdConfiguration.KEY_CMCD_REQUEST);

    // nor should be inner list format
    assertThat(requestHeader).contains("nor=(\"../seg.mp4\";r=0-500)");
    // nrr should NOT be present
    assertThat(requestHeader).doesNotContain("nrr");
  }

  /**
   * Verifies end-to-end header serialization with v1 nor (scalar) produces correct output and
   * includes nrr.
   */
  @Test
  public void v1Mode_headerSerialization_norStringAndNrrPresent() {
    CmcdV2Data data =
        new CmcdV2Data.Factory(V2_CONFIGURATION, CmcdData.STREAMING_FORMAT_DASH)
            .setNextObjectRequest("../seg.mp4")
            .setNextRangeRequest("0-500")
            .createCmcdData();

    ImmutableMap<String, String> headers = CmcdSerializer.toHeaders(data);
    String requestHeader = headers.get(CmcdConfiguration.KEY_CMCD_REQUEST);

    // nor should be double-quoted string format
    assertThat(requestHeader).contains("nor=\"../seg.mp4\"");
    // nrr should be present as separate key
    assertThat(requestHeader).contains("nrr=\"0-500\"");
  }
}
