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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integration tests for CMCD backward compatibility.
 *
 * <p>Verifies that v1 {@link CmcdConfiguration.Factory} usage produces identical output to the
 * existing implementation, that no {@code v} key appears in v1 payloads, and that header and query
 * parameter modes remain unchanged. Also verifies body mode for Event Mode produces the correct
 * LF-separated query-argument format.
 */
@RunWith(AndroidJUnit4.class)
public class CmcdBackwardCompatibilityTest {

  // =============================================================================================
  // Compile-time check: CmcdConfiguration.Factory extends CmcdConfigurationFactory
  // =============================================================================================

  @Test
  public void cmcdConfigurationFactory_extendsCmcdConfigurationFactory() {
    // This test verifies at compile time that CmcdConfiguration.Factory extends
    // CmcdConfigurationFactory. If the interface hierarchy is broken, this test will not compile.
    CmcdConfiguration.Factory v1Factory = CmcdConfiguration.Factory.DEFAULT;
    CmcdConfigurationFactory baseFactory = v1Factory;
    assertThat(baseFactory).isNotNull();
  }

  // =============================================================================================
  // V1 Header Mode: no 'v' key, correct output format
  // =============================================================================================

  @Test
  public void v1HeaderMode_producesIdenticalOutputToExistingImplementation() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem ->
            new CmcdConfiguration(
                "sessionId",
                mediaItem.mediaId,
                new CmcdConfiguration.RequestConfig() {
                  @Override
                  public int getRequestedMaximumThroughputKbps(int throughputKbps) {
                    return 2 * throughputKbps;
                  }
                });
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    ExoTrackSelection trackSelection = mock(ExoTrackSelection.class);
    Format format =
        new Format.Builder().setPeakBitrate(840_000).setSampleMimeType(MimeTypes.AUDIO_AC4).build();
    when(trackSelection.getSelectedFormat()).thenReturn(format);
    when(trackSelection.getTrackGroup())
        .thenReturn(new TrackGroup(format, new Format.Builder().setPeakBitrate(1_000_000).build()));
    when(trackSelection.getLatestBitrateEstimate()).thenReturn(500_000L);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();
    CmcdData cmcdData =
        new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_DASH)
            .setTrackSelection(trackSelection)
            .setBufferedDurationUs(1_760_000)
            .setPlaybackRate(2.0f)
            .setIsLive(true)
            .setDidRebuffer(true)
            .setIsBufferEmpty(false)
            .setChunkDurationUs(3_000_000)
            .createCmcdData();

    dataSpec = cmcdData.addToDataSpec(dataSpec);

    // Verify exact v1 output unchanged
    assertThat(dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=840,d=3000,ot=a,tb=1000",
            "CMCD-Request",
            "bl=1800,dl=900,mtp=500,su",
            "CMCD-Session",
            "cid=\"mediaId\",pr=2.00,sf=d,sid=\"sessionId\",st=l",
            "CMCD-Status",
            "bs,rtp=1700");
  }

  @Test
  public void v1HeaderMode_doesNotContainVersionKey() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem ->
            new CmcdConfiguration(
                "sessionId", mediaItem.mediaId, new CmcdConfiguration.RequestConfig() {});
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    ExoTrackSelection trackSelection = mock(ExoTrackSelection.class);
    Format format =
        new Format.Builder().setPeakBitrate(840_000).setSampleMimeType(MimeTypes.AUDIO_AC4).build();
    when(trackSelection.getSelectedFormat()).thenReturn(format);
    when(trackSelection.getTrackGroup())
        .thenReturn(new TrackGroup(format, new Format.Builder().setPeakBitrate(1_000_000).build()));
    when(trackSelection.getLatestBitrateEstimate()).thenReturn(500_000L);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();
    CmcdData cmcdData =
        new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_DASH)
            .setTrackSelection(trackSelection)
            .setBufferedDurationUs(1_760_000)
            .setPlaybackRate(2.0f)
            .setIsLive(true)
            .setDidRebuffer(true)
            .setIsBufferEmpty(false)
            .setChunkDurationUs(3_000_000)
            .createCmcdData();

    dataSpec = cmcdData.addToDataSpec(dataSpec);

    // Verify no 'v' key in any header value
    for (String headerValue : dataSpec.httpRequestHeaders.values()) {
      assertThat(headerValue).doesNotContain("v=");
      // Also ensure the version key constant doesn't appear as a key-value pair
      assertThat(headerValue).doesNotContain(",v=");
      assertThat(headerValue).doesNotMatch("^v=.*");
    }
  }

  @Test
  public void v1HeaderMode_manifestObjectType_producesCorrectOutput() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem ->
            new CmcdConfiguration(
                "sessionId", mediaItem.mediaId, new CmcdConfiguration.RequestConfig() {});
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();
    CmcdData cmcdData =
        new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_DASH)
            .setObjectType(CmcdData.OBJECT_TYPE_MANIFEST)
            .createCmcdData();

    dataSpec = cmcdData.addToDataSpec(dataSpec);

    assertThat(dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object", "ot=m", "CMCD-Session", "cid=\"mediaId\",sf=d,sid=\"sessionId\"");
  }

  // =============================================================================================
  // V1 Query Parameter Mode: no 'v' key, correct format
  // =============================================================================================

  @Test
  public void v1QueryParameterMode_producesIdenticalOutputToExistingImplementation() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem ->
            new CmcdConfiguration(
                "sessionId",
                mediaItem.mediaId,
                new CmcdConfiguration.RequestConfig() {
                  @Override
                  public int getRequestedMaximumThroughputKbps(int throughputKbps) {
                    return 2 * throughputKbps;
                  }
                },
                CmcdConfiguration.MODE_QUERY_PARAMETER);
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    ExoTrackSelection trackSelection = mock(ExoTrackSelection.class);
    Format format =
        new Format.Builder().setPeakBitrate(840_000).setSampleMimeType(MimeTypes.AUDIO_AC4).build();
    when(trackSelection.getSelectedFormat()).thenReturn(format);
    when(trackSelection.getTrackGroup())
        .thenReturn(new TrackGroup(format, new Format.Builder().setPeakBitrate(1_000_000).build()));
    when(trackSelection.getLatestBitrateEstimate()).thenReturn(500_000L);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();
    CmcdData cmcdData =
        new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_DASH)
            .setObjectType(CmcdData.OBJECT_TYPE_AUDIO_ONLY)
            .setTrackSelection(trackSelection)
            .setBufferedDurationUs(1_760_000)
            .setPlaybackRate(2.0f)
            .setIsLive(true)
            .setDidRebuffer(true)
            .setIsBufferEmpty(false)
            .setChunkDurationUs(3_000_000)
            .createCmcdData();

    dataSpec = cmcdData.addToDataSpec(dataSpec);

    assertThat(dataSpec.uri.getQueryParameter(CmcdConfiguration.CMCD_QUERY_PARAMETER_KEY))
        .isEqualTo(
            "bl=1800,br=840,bs,cid=\"mediaId\",d=3000,dl=900,mtp=500,ot=a,pr=2.00,"
                + "rtp=1700,sf=d,sid=\"sessionId\",st=l,su,tb=1000");
  }

  @Test
  public void v1QueryParameterMode_doesNotContainVersionKey() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem ->
            new CmcdConfiguration(
                "sessionId",
                mediaItem.mediaId,
                new CmcdConfiguration.RequestConfig() {},
                CmcdConfiguration.MODE_QUERY_PARAMETER);
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    ExoTrackSelection trackSelection = mock(ExoTrackSelection.class);
    Format format =
        new Format.Builder().setPeakBitrate(840_000).setSampleMimeType(MimeTypes.AUDIO_AC4).build();
    when(trackSelection.getSelectedFormat()).thenReturn(format);
    when(trackSelection.getTrackGroup())
        .thenReturn(new TrackGroup(format, new Format.Builder().setPeakBitrate(1_000_000).build()));
    when(trackSelection.getLatestBitrateEstimate()).thenReturn(500_000L);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();
    CmcdData cmcdData =
        new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_DASH)
            .setTrackSelection(trackSelection)
            .setBufferedDurationUs(1_760_000)
            .setPlaybackRate(2.0f)
            .setIsLive(true)
            .setDidRebuffer(true)
            .setIsBufferEmpty(false)
            .setChunkDurationUs(3_000_000)
            .createCmcdData();

    dataSpec = cmcdData.addToDataSpec(dataSpec);

    String queryValue =
        dataSpec.uri.getQueryParameter(CmcdConfiguration.CMCD_QUERY_PARAMETER_KEY);
    assertThat(queryValue).isNotNull();
    // 'v' key should never appear in v1 payloads
    assertThat(queryValue).doesNotContain(",v=");
    assertThat(queryValue).doesNotMatch("^v=.*");
  }

  // =============================================================================================
  // V1 'nor' and 'nrr' format verification
  // =============================================================================================

  @Test
  public void v1HeaderMode_norFormattedAsSingleQuotedString() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem ->
            new CmcdConfiguration(
                "sessionId", mediaItem.mediaId, new CmcdConfiguration.RequestConfig() {});
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    ExoTrackSelection trackSelection = mock(ExoTrackSelection.class);
    Format format =
        new Format.Builder().setPeakBitrate(840_000).setSampleMimeType(MimeTypes.AUDIO_AC4).build();
    when(trackSelection.getSelectedFormat()).thenReturn(format);
    when(trackSelection.getTrackGroup())
        .thenReturn(new TrackGroup(format, new Format.Builder().setPeakBitrate(1_000_000).build()));
    when(trackSelection.getLatestBitrateEstimate()).thenReturn(500_000L);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();
    CmcdData cmcdData =
        new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_DASH)
            .setTrackSelection(trackSelection)
            .setBufferedDurationUs(1_760_000)
            .setPlaybackRate(2.0f)
            .setIsLive(false)
            .setDidRebuffer(false)
            .setIsBufferEmpty(false)
            .setChunkDurationUs(3_000_000)
            .setNextObjectRequest("../seg.mp4")
            .setNextRangeRequest("100-200")
            .createCmcdData();

    dataSpec = cmcdData.addToDataSpec(dataSpec);

    // In v1 header mode, nor is a double-quoted string with URL-encoded value, and nrr is separate
    String requestHeader = dataSpec.httpRequestHeaders.get("CMCD-Request");
    assertThat(requestHeader).isNotNull();
    // The v1 implementation URL-encodes the nor value
    assertThat(requestHeader).contains("nor=\"..%2Fseg.mp4\"");
    assertThat(requestHeader).contains("nrr=\"100-200\"");
  }

  @Test
  public void v1QueryParameterMode_norAndNrrPresentCorrectly() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem ->
            new CmcdConfiguration(
                "sessionId",
                mediaItem.mediaId,
                new CmcdConfiguration.RequestConfig() {},
                CmcdConfiguration.MODE_QUERY_PARAMETER);
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    ExoTrackSelection trackSelection = mock(ExoTrackSelection.class);
    Format format =
        new Format.Builder().setPeakBitrate(840_000).setSampleMimeType(MimeTypes.AUDIO_AC4).build();
    when(trackSelection.getSelectedFormat()).thenReturn(format);
    when(trackSelection.getTrackGroup())
        .thenReturn(new TrackGroup(format, new Format.Builder().setPeakBitrate(1_000_000).build()));
    when(trackSelection.getLatestBitrateEstimate()).thenReturn(500_000L);
    DataSpec dataSpec = new DataSpec.Builder().setUri(Uri.EMPTY).build();
    CmcdData cmcdData =
        new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_DASH)
            .setTrackSelection(trackSelection)
            .setBufferedDurationUs(1_760_000)
            .setPlaybackRate(2.0f)
            .setIsLive(false)
            .setDidRebuffer(false)
            .setIsBufferEmpty(false)
            .setChunkDurationUs(3_000_000)
            .setNextObjectRequest("../seg.mp4")
            .setNextRangeRequest("100-200")
            .createCmcdData();

    dataSpec = cmcdData.addToDataSpec(dataSpec);

    String queryValue =
        dataSpec.uri.getQueryParameter(CmcdConfiguration.CMCD_QUERY_PARAMETER_KEY);
    assertThat(queryValue).isNotNull();
    // nor as single double-quoted string (URL-encoded in v1), nrr as separate key
    assertThat(queryValue).contains("nor=\"..%2Fseg.mp4\"");
    assertThat(queryValue).contains("nrr=\"100-200\"");
  }

  // =============================================================================================
  // V1 Default Factory unchanged behavior
  // =============================================================================================

  @Test
  public void v1DefaultFactory_producesValidConfiguration() {
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("testContent").build();
    CmcdConfiguration cmcdConfiguration =
        CmcdConfiguration.Factory.DEFAULT.createCmcdConfiguration(mediaItem);

    assertThat(cmcdConfiguration).isNotNull();
    assertThat(cmcdConfiguration.contentId).isEqualTo("testContent");
    assertThat(cmcdConfiguration.sessionId).isNotNull();
    assertThat(cmcdConfiguration.sessionId.length()).isAtMost(CmcdConfiguration.MAX_ID_LENGTH);
    assertThat(cmcdConfiguration.dataTransmissionMode)
        .isEqualTo(CmcdConfiguration.MODE_REQUEST_HEADER);
  }

  @Test
  public void v1DefaultFactory_onDefaultMediaSourceFactory_producesV1Output() {
    // Simulate what DefaultMediaSourceFactory does with a v1 factory
    CmcdConfiguration.Factory v1Factory = CmcdConfiguration.Factory.DEFAULT;
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("content-1").build();
    CmcdConfiguration config = v1Factory.createCmcdConfiguration(mediaItem);

    assertThat(config).isNotNull();
    assertThat(config).isNotInstanceOf(CmcdV2Configuration.class);
    assertThat(config.dataTransmissionMode).isEqualTo(CmcdConfiguration.MODE_REQUEST_HEADER);
  }

  // =============================================================================================
  // Body Mode for Event Mode: LF-separated query-argument format (v2 specific)
  // =============================================================================================

  @Test
  public void bodyMode_eventModeData_producesCorrectLfSeparatedFormat() {
    CmcdV2Configuration configuration =
        new CmcdV2Configuration.Builder()
            .setSessionId("session-1")
            .setContentId("content-1")
            .setVersionMode(CmcdV2Keys.VERSION_MODE_V2_ONLY)
            .build();
    CmcdV2Data v2Data =
        new CmcdV2Data.Factory(configuration, CmcdData.STREAMING_FORMAT_DASH)
            .setBitrate(5000)
            .setBufferLength(1800)
            .setEventType(CmcdV2Keys.EVENT_TYPE_PLAY_STATE_CHANGE)
            .setTimestampMs(1700000000000L)
            .createCmcdData();

    String body = CmcdSerializer.toBody(v2Data);

    // Body mode: all keys combined in a single record as comma-separated key=value pairs
    // No URL encoding, no header group prefixes, keys sorted alphabetically
    assertThat(body).isNotEmpty();
    // Verify key=value format (not URL-encoded)
    assertThat(body).contains("br=5000");
    assertThat(body).contains("bl=1800");
    // Token/string values are double-quoted per CMCD formatting rules
    assertThat(body).contains("e=\"ps\"");
    assertThat(body).contains("ts=1700000000000");
    assertThat(body).contains("v=2");
    assertThat(body).contains("sf=\"d\"");
    assertThat(body).contains("sid=\"session-1\"");
    assertThat(body).contains("cid=\"content-1\"");
    // Verify it is NOT URL-encoded (no percent-encoding)
    assertThat(body).doesNotContain("%3D");
    assertThat(body).doesNotContain("%2C");
  }

  @Test
  public void bodyMode_singleRecord_noTrailingLf() {
    CmcdV2Configuration configuration =
        new CmcdV2Configuration.Builder()
            .setSessionId("session-1")
            .setVersionMode(CmcdV2Keys.VERSION_MODE_V2_ONLY)
            .build();
    CmcdV2Data v2Data =
        new CmcdV2Data.Factory(configuration, CmcdData.STREAMING_FORMAT_DASH)
            .setBitrate(3000)
            .setEventType(CmcdV2Keys.EVENT_TYPE_BITRATE_CHANGE)
            .setTimestampMs(1700000000000L)
            .createCmcdData();

    String body = CmcdSerializer.toBody(v2Data);

    // Single record should not have trailing LF
    assertThat(body).isNotEmpty();
    assertThat(body).doesNotMatch(".*\\n$");
    // Verify comma-separated format within the single record
    assertThat(body).contains(",");
  }

  @Test
  public void bodyMode_emptyData_producesEmptyOutput() {
    CmcdV2Configuration configuration =
        new CmcdV2Configuration.Builder()
            .setVersionMode(CmcdV2Keys.VERSION_MODE_V2_ONLY)
            .build();
    // Create a minimal CmcdKeyValueStore with empty maps
    CmcdKeyValueStore emptyStore =
        new CmcdKeyValueStore() {
          @Override
          public ImmutableMap<String, Object> getObjectKeys() {
            return ImmutableMap.of();
          }

          @Override
          public ImmutableMap<String, Object> getRequestKeys() {
            return ImmutableMap.of();
          }

          @Override
          public ImmutableMap<String, Object> getSessionKeys() {
            return ImmutableMap.of();
          }

          @Override
          public ImmutableMap<String, Object> getStatusKeys() {
            return ImmutableMap.of();
          }

          @Override
          public ImmutableMap<String, Object> getEventOnlyKeys() {
            return ImmutableMap.of();
          }
        };

    String body = CmcdSerializer.toBody(emptyStore);

    // Empty records should produce empty output
    assertThat(body).isEmpty();
  }

  // =============================================================================================
  // V1 content ID length constraint (64 chars max)
  // =============================================================================================

  @Test
  public void v1Configuration_contentIdMaxLength64() {
    // 64-character content ID should be accepted
    String validContentId = "a".repeat(64);
    CmcdConfiguration config =
        new CmcdConfiguration("sid", validContentId, new CmcdConfiguration.RequestConfig() {});
    assertThat(config.contentId).isEqualTo(validContentId);
  }

  @Test
  public void v1Configuration_contentIdExceeding64_throws() {
    // 65-character content ID should throw
    String invalidContentId = "a".repeat(65);
    try {
      new CmcdConfiguration("sid", invalidContentId, new CmcdConfiguration.RequestConfig() {});
      assertThat(true).isFalse(); // Should not reach here
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }
}
