# CMCD (Common Media Client Data) Implementation Guide — AndroidX Media3

This document provides a detailed technical reference for how CMCD v1 (CTA-5004) is implemented,
configured, and tested in the AndroidX Media3 / ExoPlayer codebase. It is intended to serve as
a baseline for implementing CMCD v2.

---

## 1. Specification Reference

The implementation follows **CTA-5004** (Common Media Client Data).  
Document: https://cdn.cta.tech/cta/media/media/resources/standards/pdfs/cta-5004-final.pdf

The version constant is hardcoded:

```java
// CmcdData.CmcdSession
public static final int VERSION = 1;
```

If `VERSION == 1`, the `v` key is **omitted** from the payload per spec.

---

## 2. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                      Developer API                                │
│  MediaSource.Factory.setCmcdConfigurationFactory(factory)         │
└───────────────────────────────┬──────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│              CmcdConfiguration.Factory                            │
│  Creates CmcdConfiguration per MediaItem                         │
└───────────────────────────────┬──────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│                   CmcdConfiguration                              │
│  sessionId, contentId, RequestConfig, dataTransmissionMode       │
└───────────────────────────────┬──────────────────────────────────┘
                                │
          ┌─────────────────────┼─────────────────────┐
          ▼                     ▼                     ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ DashMediaSource │  │ HlsMediaSource  │  │  SsMediaSource  │
│ DashChunkSource │  │ HlsChunkSource  │  │  SsChunkSource  │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         └─────────────────── ┼ ───────────────────┘
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                     CmcdData.Factory                              │
│  Builder-pattern, assembles CmcdObject/Request/Session/Status    │
└───────────────────────────────┬──────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│                        CmcdData                                  │
│  addToDataSpec(dataSpec) → attaches to HTTP headers or query     │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. File Locations

| File | Purpose |
|------|---------|
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/upstream/CmcdConfiguration.java` | Configuration model (session/content IDs, request config, data transmission mode, CMCD key constants) |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/upstream/CmcdData.java` | Data assembly + HTTP attachment logic (Factory, inner model classes, addToDataSpec/removeFromDataSpec) |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/MediaSource.java` | `setCmcdConfigurationFactory()` API on `MediaSource.Factory` |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/DefaultMediaSourceFactory.java` | Propagates CmcdConfiguration.Factory to per-format media source factories |
| `libraries/exoplayer_dash/src/main/java/androidx/media3/exoplayer/dash/DashMediaSource.java` | DASH integration: manifest requests + passes config to chunk source |
| `libraries/exoplayer_dash/src/main/java/androidx/media3/exoplayer/dash/DefaultDashChunkSource.java` | DASH chunk requests: media/init segments |
| `libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaSource.java` | HLS integration: passes config down |
| `libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsChunkSource.java` | HLS chunk requests: media/init/encryption key segments |
| `libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaChunk.java` | HLS media chunk creation with CMCD |
| `libraries/exoplayer_smoothstreaming/src/main/java/androidx/media3/exoplayer/smoothstreaming/SsMediaSource.java` | SmoothStreaming integration |
| `libraries/exoplayer_smoothstreaming/src/main/java/androidx/media3/exoplayer/smoothstreaming/DefaultSsChunkSource.java` | SS chunk requests |

### Test Files

| File | Purpose |
|------|---------|
| `libraries/exoplayer/src/test/java/androidx/media3/exoplayer/upstream/CmcdConfigurationTest.java` | Unit tests for configuration validation |
| `libraries/exoplayer/src/test/java/androidx/media3/exoplayer/upstream/CmcdDataTest.java` | Unit tests for data assembly and HTTP attachment |
| `libraries/exoplayer_dash/src/test/java/androidx/media3/exoplayer/dash/DefaultDashChunkSourceTest.java` | Integration test: CMCD with DASH chunk source |
| `libraries/exoplayer_dash/src/test/java/androidx/media3/exoplayer/dash/e2etest/DashPlaybackTest.java` | E2E test with CMCD enabled |
| `libraries/exoplayer_hls/src/test/java/androidx/media3/exoplayer/hls/e2etest/HlsPlaybackTest.java` | E2E test with CMCD enabled |

### Doc Samples

| File | Purpose |
|------|---------|
| `docsamples/src/main/java/androidx/media3/docsamples/exoplayer/Cmcd.java` | Java usage examples |
| `docsamples/src/main/java/androidx/media3/docsamples/exoplayer/Cmcd.kt` | Kotlin usage examples |

---

## 4. CmcdConfiguration — The Configuration Layer

### 4.1 Class Structure

```java
@UnstableApi
public final class CmcdConfiguration {
    @Nullable public final String sessionId;       // max 64 chars
    @Nullable public final String contentId;       // max 64 chars
    public final RequestConfig requestConfig;
    public final @DataTransmissionMode int dataTransmissionMode;
}
```

### 4.2 Data Transmission Modes

```java
public static final int MODE_REQUEST_HEADER = 0;   // Default: 4 HTTP headers
public static final int MODE_QUERY_PARAMETER = 1;  // Single "CMCD" query param
```

### 4.3 Factory Interface

```java
public interface Factory {
    @Nullable
    CmcdConfiguration createCmcdConfiguration(MediaItem mediaItem);

    // Default implementation: random sessionId, mediaItem.mediaId as contentId
    CmcdConfiguration.Factory DEFAULT = mediaItem -> new CmcdConfiguration(
        UUID.randomUUID().toString(),
        mediaItem.mediaId != null ? mediaItem.mediaId : MediaItem.DEFAULT_MEDIA_ID,
        new RequestConfig() {});
}
```

Returning `null` from `createCmcdConfiguration()` **disables CMCD** for that media item.

### 4.4 RequestConfig Interface

```java
public interface RequestConfig {
    // Filter which CMCD keys are sent (default: all allowed)
    default boolean isKeyAllowed(@CmcdKey String key) { return true; }

    // Provide custom key-value data per header group
    default ImmutableListMultimap<@HeaderKey String, String> getCustomData() {
        return ImmutableListMultimap.of();
    }

    // Set maximum requested throughput (default: C.RATE_UNSET_INT = not sent)
    default int getRequestedMaximumThroughputKbps(int throughputKbps) {
        return C.RATE_UNSET_INT;
    }
}
```

### 4.5 Key Constants

| Constant | CMCD Key | Header Group | Description |
|----------|----------|--------------|-------------|
| `KEY_BITRATE` | `br` | CMCD-Object | Encoded bitrate (kbps) |
| `KEY_TOP_BITRATE` | `tb` | CMCD-Object | Highest available bitrate (kbps) |
| `KEY_OBJECT_DURATION` | `d` | CMCD-Object | Object playback duration (ms) |
| `KEY_OBJECT_TYPE` | `ot` | CMCD-Object | Media object type |
| `KEY_BUFFER_LENGTH` | `bl` | CMCD-Request | Buffer length (ms) |
| `KEY_MEASURED_THROUGHPUT` | `mtp` | CMCD-Request | Measured throughput (kbps) |
| `KEY_DEADLINE` | `dl` | CMCD-Request | Time until buffer underrun (ms) |
| `KEY_STARTUP` | `su` | CMCD-Request | Startup/rebuffer flag |
| `KEY_NEXT_OBJECT_REQUEST` | `nor` | CMCD-Request | Relative URL of next request |
| `KEY_NEXT_RANGE_REQUEST` | `nrr` | CMCD-Request | Byte range of next request |
| `KEY_CONTENT_ID` | `cid` | CMCD-Session | Content identifier |
| `KEY_SESSION_ID` | `sid` | CMCD-Session | Session identifier |
| `KEY_STREAMING_FORMAT` | `sf` | CMCD-Session | Streaming format (d/h/s) |
| `KEY_STREAM_TYPE` | `st` | CMCD-Session | Stream type (v/l) |
| `KEY_PLAYBACK_RATE` | `pr` | CMCD-Session | Playback speed |
| `KEY_VERSION` | `v` | CMCD-Session | CMCD spec version |
| `KEY_MAXIMUM_REQUESTED_BITRATE` | `rtp` | CMCD-Status | Max requested throughput (kbps) |
| `KEY_BUFFER_STARVATION` | `bs` | CMCD-Status | Buffer starvation occurred |

### 4.6 Convenience Logging Methods

`CmcdConfiguration` exposes per-key `isXxxLoggingAllowed()` methods that delegate to
`requestConfig.isKeyAllowed(key)`:

```java
public boolean isBitrateLoggingAllowed()         // KEY_BITRATE
public boolean isBufferLengthLoggingAllowed()     // KEY_BUFFER_LENGTH
public boolean isContentIdLoggingAllowed()        // KEY_CONTENT_ID
public boolean isSessionIdLoggingAllowed()        // KEY_SESSION_ID
// ... one per key
```

---

## 5. CmcdData — The Data Assembly Layer

### 5.1 Class Overview

`CmcdData` is an immutable value object composed of four inner models matching the four CMCD
header groups:

- `CmcdObject` — Keys that vary per requested object (`br`, `tb`, `d`, `ot`)
- `CmcdRequest` — Keys that vary per request (`bl`, `mtp`, `dl`, `su`, `nor`, `nrr`)
- `CmcdSession` — Keys invariant over session lifetime (`cid`, `sid`, `sf`, `st`, `pr`, `v`)
- `CmcdStatus` — Keys that don't vary with every request (`rtp`, `bs`)

### 5.2 CmcdData.Factory (Builder)

The `Factory` is a mutable builder used by chunk sources to incrementally set fields before
calling `createCmcdData()`:

```java
public static final class Factory {
    public Factory(CmcdConfiguration cmcdConfiguration, @StreamingFormat String streamingFormat);

    // Object-level
    public Factory setObjectType(@Nullable @ObjectType String objectType);
    public Factory setChunkDurationUs(long chunkDurationUs);

    // Request-level
    public Factory setTrackSelection(ExoTrackSelection trackSelection);
    public Factory setBufferedDurationUs(long bufferedDurationUs);
    public Factory setPlaybackRate(float playbackRate);
    public Factory setNextObjectRequest(@Nullable String nextObjectRequest);
    public Factory setNextRangeRequest(@Nullable String nextRangeRequest);

    // Session-level
    public Factory setIsLive(boolean isLive);

    // Status-level
    public Factory setDidRebuffer(boolean didRebuffer);
    public Factory setIsBufferEmpty(boolean isBufferEmpty);

    public CmcdData createCmcdData();
}
```

#### Preconditions in `createCmcdData()`

- If `objectType` is NOT manifest: `trackSelection` **must** be non-null.
- If `objectType` is audio/video/muxed: `bufferedDurationUs` and `chunkDurationUs` **must** be set.
- Custom data keys must match pattern `.*-.*` (hyphenated prefix).

#### Object Type Inference

If `objectType` is not explicitly set, it is inferred from `trackSelection.getSelectedFormat()`:

```java
private static @ObjectType String getObjectTypeFromFormat(Format format) {
    // Checks codecs for audio+video → "av"
    // Checks sampleMimeType/containerMimeType track type → "a" or "v"
    // Falls back to null (unknown)
}
```

### 5.3 Streaming Format Constants

```java
public static final String STREAMING_FORMAT_DASH = "d";
public static final String STREAMING_FORMAT_HLS = "h";
public static final String STREAMING_FORMAT_SS = "s";
```

### 5.4 Object Type Constants

```java
public static final String OBJECT_TYPE_INIT_SEGMENT = "i";
public static final String OBJECT_TYPE_AUDIO_ONLY = "a";
public static final String OBJECT_TYPE_VIDEO_ONLY = "v";
public static final String OBJECT_TYPE_MUXED_AUDIO_AND_VIDEO = "av";
public static final String OBJECT_TYPE_MANIFEST = "m";
```

### 5.5 Stream Type Constants

```java
public static final String STREAM_TYPE_VOD = "v";
public static final String STREAM_TYPE_LIVE = "l";
```

### 5.6 Data Rounding Rules

Per CTA-5004 spec, certain values are rounded:

| Field | Rounding |
|-------|----------|
| `bufferLengthMs` | Nearest 100 ms |
| `measuredThroughputInKbps` | Nearest 100 kbps |
| `deadlineMs` | Nearest 100 ms |
| `maximumRequestedThroughputKbps` | Nearest 100 kbps |

Rounding formula: `((value + 50) / 100) * 100`

### 5.7 Playback Rate Behavior

The `pr` key is **omitted** if `playbackRate == 1.0f` (normal speed) per spec guidance.

---

## 6. HTTP Attachment — `addToDataSpec()`

### 6.1 Header Mode (`MODE_REQUEST_HEADER`)

Produces up to 4 HTTP request headers. Each header contains comma-separated key-value pairs
sorted alphabetically:

```
CMCD-Object: br=840,d=3000,ot=v,tb=1000
CMCD-Request: bl=1800,dl=900,mtp=500,su
CMCD-Session: cid="mediaId",pr=2.00,sf=d,sid="sessionId",st=l
CMCD-Status: bs,rtp=1700
```

Empty headers are **not sent**.

### 6.2 Query Parameter Mode (`MODE_QUERY_PARAMETER`)

Produces a single `CMCD` query parameter with **all** key-value pairs flattened and sorted
alphabetically:

```
?CMCD=bl%3D1800%2Cbr%3D840%2Cbs%2Ccid%3D%22mediaId%22%2Cd%3D3000%2C...
```

The value is URL-encoded.

### 6.3 Value Formatting Rules

| Type | Format | Example |
|------|--------|---------|
| Integer | bare number | `br=840` |
| Boolean (true) | key only, no `=` | `su`, `bs` |
| Boolean (false) | key omitted entirely | — |
| String | double-quoted | `cid="mediaId"` |
| Float | 2 decimal places | `pr=2.00` |
| URL (nor) | double-quoted, URL-encoded | `nor="..%2Fvideo.m4s"` |

---

## 7. Removal — `removeFromDataSpec()` and `removeFromUri()`

Used internally (e.g., in `HlsChunkSource.shouldReplaceChunk()`) to compare URIs without
CMCD data:

```java
// Remove CMCD from DataSpec (both headers and query param)
public static DataSpec removeFromDataSpec(DataSpec dataSpec);

// Remove CMCD query param from Uri only
public static Uri removeFromUri(Uri uri);
```

`removeFromUri` checks:
1. URI must be hierarchical
2. Must have a `CMCD` query parameter (case-sensitive)
3. Non-hierarchical URIs (e.g., `data:`) are returned unmodified

---

## 8. Integration Points per Streaming Format

### 8.1 DASH

**Manifest requests** (`DashMediaSource`):
```java
CmcdData.Factory cmcdDataFactory =
    new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_DASH)
        .setObjectType(CmcdData.OBJECT_TYPE_MANIFEST);
if (manifest != null) {
    cmcdDataFactory.setIsLive(manifest.dynamic);
}
dataSpec = cmcdDataFactory.createCmcdData().addToDataSpec(dataSpec);
```

**Media chunk requests** (`DefaultDashChunkSource`):
```java
CmcdData.Factory cmcdDataFactory =
    new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_DASH)
        .setTrackSelection(trackSelection)
        .setBufferedDurationUs(max(0, bufferedDurationUs))
        .setPlaybackRate(loadingInfo.playbackSpeed)
        .setIsLive(manifest.dynamic)
        .setDidRebuffer(loadingInfo.rebufferedSince(lastChunkRequestRealtimeMs))
        .setIsBufferEmpty(queue.isEmpty());
```

Additional fields set for media chunks:
- `setChunkDurationUs()` from segment duration
- `setNextObjectRequest()` relative path to next segment
- `setNextRangeRequest()` byte range for next segment

**Init segment requests**: `setObjectType(CmcdData.OBJECT_TYPE_INIT_SEGMENT)` is applied.

### 8.2 HLS

**Media chunk requests** (`HlsChunkSource`):
```java
CmcdData.Factory cmcdDataFactory =
    new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_HLS)
        .setTrackSelection(trackSelection)
        .setBufferedDurationUs(max(0, bufferedDurationUs))
        .setPlaybackRate(loadingInfo.playbackSpeed)
        .setIsLive(!playlist.hasEndTag)
        .setDidRebuffer(loadingInfo.rebufferedSince(lastChunkRequestRealtimeMs))
        .setIsBufferEmpty(queue.isEmpty());
```

Also sets `setNextObjectRequest()` and `setNextRangeRequest()` when the next segment is known.

**Init segments** (`HlsMediaChunk.createInstance`): Reuses the same `cmcdDataFactory` with
`setObjectType(CmcdData.OBJECT_TYPE_INIT_SEGMENT)`.

**Encryption key requests**: CMCD is attached to AES key loading requests as well, using the
init segment object type if the key is for an init segment.

**Note**: HLS does NOT send CMCD on playlist/manifest requests (no playlist tracker integration).

### 8.3 SmoothStreaming

**Media chunk requests** (`DefaultSsChunkSource`):
```java
CmcdData.Factory cmcdDataFactory =
    new CmcdData.Factory(cmcdConfiguration, CmcdData.STREAMING_FORMAT_SS)
        .setTrackSelection(trackSelection)
        .setBufferedDurationUs(max(0, bufferedDurationUs))
        .setPlaybackRate(loadingInfo.playbackSpeed)
        .setIsLive(!manifest.isLive)  // inverted: SS uses `isLive` on manifest
        .setDidRebuffer(loadingInfo.rebufferedSince(lastChunkRequestRealtimeMs))
        .setIsBufferEmpty(queue.isEmpty());
```

Also sets `setNextObjectRequest()` for next chunk.

---

## 9. Configuration API (Developer-Facing)

### 9.1 Default Configuration (Simplest)

```java
ExoPlayer player = new ExoPlayer.Builder(context)
    .setMediaSourceFactory(
        new DefaultMediaSourceFactory(context)
            .setCmcdConfigurationFactory(CmcdConfiguration.Factory.DEFAULT))
    .build();
```

This sends all CMCD keys via HTTP headers with a random session ID and `mediaItem.mediaId`
as content ID.

### 9.2 Custom Configuration

```java
CmcdConfiguration.Factory cmcdConfigurationFactory = mediaItem -> {
    CmcdConfiguration.RequestConfig requestConfig = new CmcdConfiguration.RequestConfig() {
        @Override
        public boolean isKeyAllowed(String key) {
            // Only send bitrate and buffer length
            return key.equals("br") || key.equals("bl");
        }

        @Override
        public ImmutableListMultimap<@HeaderKey String, String> getCustomData() {
            return ImmutableListMultimap.of(
                CmcdConfiguration.KEY_CMCD_OBJECT, "key1=stringValue");
        }

        @Override
        public int getRequestedMaximumThroughputKbps(int throughputKbps) {
            return 5 * throughputKbps;
        }
    };

    return new CmcdConfiguration(
        UUID.randomUUID().toString(),  // sessionId
        mediaItem.mediaId,             // contentId
        requestConfig,
        CmcdConfiguration.MODE_QUERY_PARAMETER);  // or MODE_REQUEST_HEADER
};

new DefaultMediaSourceFactory(context).setCmcdConfigurationFactory(cmcdConfigurationFactory);
```

### 9.3 Disabling CMCD for Specific Media Items

```java
CmcdConfiguration.Factory factory = mediaItem -> {
    if (shouldDisableCmcd(mediaItem)) return null;
    return CmcdConfiguration.Factory.DEFAULT.createCmcdConfiguration(mediaItem);
};
```

---

## 10. Custom Data Rules

Custom key-value pairs must follow these rules:

1. **Keys MUST contain a hyphen** (validated by regex `.*-.*`). This prevents namespace
   collisions with spec keys.
2. Clients SHOULD use reverse-DNS prefix (e.g., `com.example-key1`).
3. Custom data is allocated to one of the four header groups via the `ImmutableListMultimap`
   returned by `getCustomData()`.
4. String values must be enclosed in double quotes.
5. Boolean true values omit the `=value` part.

Example custom data output:
```
CMCD-Object: key-1=1,key-2-separated-by-multiple-hyphens=2,ot=m
CMCD-Session: key-4=0.5,sf=d
```

Invalid custom key (no hyphen) throws `IllegalStateException` at `createCmcdData()` time.

---

## 11. Testing Strategy

### 11.1 Unit Tests (`CmcdConfigurationTest`)

Tests configuration validation:
- Invalid session ID (> 64 chars) → `IllegalArgumentException`
- Invalid content ID (> 64 chars) → `IllegalArgumentException`
- Null request config → `NullPointerException`
- Default factory behavior verification
- Custom factory with selective key allowance

### 11.2 Unit Tests (`CmcdDataTest`)

Tests data assembly and output formatting:
- Audio/video/muxed object type detection from MIME types and codecs
- Header mode output format and values
- Query parameter mode output format and URL encoding
- Manifest object type (minimal fields)
- Null/unknown object type handling
- Custom data injection and sorting
- Invalid custom data key validation
- `isKeyAllowed` filtering (no keys allowed → empty output)
- `removeFromDataSpec` with headers
- `removeFromDataSpec` with query parameters
- `removeFromUri` with and without CMCD data
- Non-hierarchical URI handling

### 11.3 Integration Tests (`DefaultDashChunkSourceTest`)

Tests CMCD within DASH chunking logic:
- Default configuration produces correct headers across multiple requests
- Buffer starvation (`bs`) key transitions: not sent → sent on rebuffer → cleared
- Custom configuration with `isKeyAllowed` filter
- Custom data in DASH context
- `nor` (next object request) relative path calculation
- `nrr` (next range request) byte range format
- Startup (`su`) key on first request and after rebuffer

### 11.4 E2E Tests (`DashPlaybackTest`, `HlsPlaybackTest`)

Full playback tests with CMCD enabled using `CmcdConfiguration.Factory.DEFAULT` to verify
no crashes or regressions during actual media playback.

---

## 12. Key Implementation Details for V2 Planning

### 12.1 Current Limitations / Notes

1. **No CMCD on HLS playlist requests**: Unlike DASH (which sends CMCD on manifest loads),
   HLS playlist tracker does NOT attach CMCD data.

2. **No `v` key sent for version 1**: The version field is suppressed when `VERSION == 1`.
   For v2, this constant should become `2` and the key should be sent.

3. **Startup (`su`) logic**: Currently `su` is true when `didRebuffer || isBufferEmpty`.
   This is computed at chunk source level.

4. **Deadline (`dl`) calculation**: `dl = bufferDuration / playbackRate` (in ms, rounded to
   nearest 100).

5. **Measured throughput (`mtp`)**: Comes from `trackSelection.getLatestBitrateEstimate()`,
   divided by 1000, rounded to nearest 100 kbps.

6. **Max requested throughput (`rtp`)**: Delegated entirely to
   `RequestConfig.getRequestedMaximumThroughputKbps(bitrateKbps)`.

7. **Buffer starvation (`bs`)**: Only `true` when `didRebuffer` is true. Tracked via
   `loadingInfo.rebufferedSince(lastChunkRequestRealtimeMs)`.

8. **`lastChunkRequestRealtimeMs`**: Each chunk source tracks when the last request was made
   to determine if a rebuffer occurred between requests.

### 12.2 Extension Points for V2

| Concern | Where to Modify |
|---------|-----------------|
| Add new CMCD keys | `CmcdConfiguration` (constants + `@CmcdKey` annotation) |
| Add new header groups | Would need new inner class in `CmcdData` + update `addToDataSpec` |
| Change version | `CmcdData.CmcdSession.VERSION` constant |
| Add JSON transmission mode | New `@DataTransmissionMode` value + handling in `addToDataSpec` |
| Add new object types | `CmcdData.@ObjectType` annotation + constants |
| Per-format-specific fields | Individual chunk source implementations |
| Server-side signaling (CMSD) | Separate new class, likely at DataSource/response level |

### 12.3 Thread Safety

From the Javadoc:
> Implementations [of Factory and RequestConfig] must not make assumptions about which thread
> called their methods; and must be thread-safe.

### 12.4 Annotation-Based Type Safety

The codebase uses `@StringDef`/`@IntDef` annotations for compile-time safety:
- `@HeaderKey` — Constrains to the 4 header names
- `@CmcdKey` — Constrains to known CMCD key strings
- `@DataTransmissionMode` — `MODE_REQUEST_HEADER` or `MODE_QUERY_PARAMETER`
- `@StreamingFormat` — `d`, `h`, `s`
- `@ObjectType` — `i`, `a`, `v`, `av`, `m`
- `@StreamType` — `v`, `l`

### 12.5 `@UnstableApi` Annotation

Both `CmcdConfiguration` and `CmcdData` are marked `@UnstableApi`, meaning they are subject
to breaking changes. This is relevant for v2 since the API surface can still evolve.

---

## 13. Data Flow Example (DASH Media Chunk)

1. `ExoPlayer` creates a `DefaultMediaSourceFactory` with a `CmcdConfiguration.Factory`.
2. `DefaultMediaSourceFactory` passes the factory to `DashMediaSource.Factory`.
3. `DashMediaSource` calls `factory.createCmcdConfiguration(mediaItem)` when preparing.
4. The `CmcdConfiguration` is passed to `DefaultDashChunkSource`.
5. When `getNextChunk()` is called:
   a. A `CmcdData.Factory` is created with streaming format `"d"`.
   b. Track selection, buffer duration, playback rate, live status, rebuffer state are set.
   c. Chunk duration and next object/range are set for media segments.
   d. `createCmcdData()` validates preconditions, builds inner objects, applies key filtering.
   e. The returned `CmcdData` is passed to chunk constructors.
6. The chunk constructor calls `cmcdData.addToDataSpec(dataSpec)`.
7. `addToDataSpec()` populates either HTTP headers or query parameter.
8. The `DataSource` sends the request with CMCD data attached.

---

## 14. Output Format Examples

### Header Mode (audio stream, DASH, live, rebuffered)

```http
GET /segment_1.m4a HTTP/1.1
CMCD-Object: br=840,d=3000,ot=a,tb=1000
CMCD-Request: bl=1800,dl=900,mtp=500,su
CMCD-Session: cid="mediaId",pr=2.00,sf=d,sid="sessionId",st=l
CMCD-Status: bs,rtp=1700
```

### Query Parameter Mode (same data)

```
/segment_1.m4a?CMCD=bl%3D1800%2Cbr%3D840%2Cbs%2Ccid%3D%22mediaId%22%2Cd%3D3000%2Cdl%3D900%2Cmtp%3D500%2Cot%3Da%2Cpr%3D2.00%2Crtp%3D1700%2Csf%3Dd%2Csid%3D%22sessionId%22%2Cst%3Dl%2Csu%2Ctb%3D1000
```

Decoded query value:
```
bl=1800,br=840,bs,cid="mediaId",d=3000,dl=900,mtp=500,ot=a,pr=2.00,rtp=1700,sf=d,sid="sessionId",st=l,su,tb=1000
```

### Manifest Request (DASH)

```http
GET /manifest.mpd HTTP/1.1
CMCD-Object: ot=m
CMCD-Session: cid="mediaId",sf=d,sid="sessionId"
```

---

## 15. Dependency Graph

```
CmcdConfiguration (no deps besides Guava ImmutableListMultimap)
    ↓
CmcdData (depends on CmcdConfiguration, DataSpec, ExoTrackSelection, Format, Uri)
    ↓
Chunk Sources (depend on CmcdData.Factory, CmcdData)
    ↓
Media Sources (depend on CmcdConfiguration, CmcdConfiguration.Factory)
    ↓
DefaultMediaSourceFactory / MediaSource.Factory API
```

External dependencies: Guava (`ImmutableListMultimap`, `ImmutableMap`, `ImmutableList`,
`ArrayListMultimap`), Android SDK (`Uri`, `TextUtils`).
