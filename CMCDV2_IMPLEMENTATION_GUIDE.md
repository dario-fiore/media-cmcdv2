# CMCD V2 (CTA-5004-B) Implementation Guide — AndroidX Media3

This document provides a detailed technical reference for how CMCD v2 (CTA-5004-B) is implemented,
configured, and tested in the AndroidX Media3 / ExoPlayer codebase. It builds upon the existing
v1 implementation (documented in `CMCD_IMPLEMENTATION_GUIDE.md`) and adds Event Mode reporting,
body transmission, inner list syntax, mixed-version mode, and expanded reserved keys.

---

## 1. Specification Reference

The implementation follows **CTA-5004-B** (Common Media Client Data, Version 2).

Key differences from CTA-5004 (v1):
- **Event Mode**: Autonomous CMCD reporting via HTTP POST to configured endpoints
- **Body Transmission**: LF-separated records in query-argument format
- **Inner List Syntax**: Per-object-type values using RFC 8941 structured fields
- **Mixed-Version Mode**: V1 syntax for Request Mode, v2 syntax for Event Mode
- **31 New Reserved Keys**: Quality metrics, buffer analytics, response tracing, session management
- **Extended Content ID**: Max 128 characters (up from 64)
- **Version Key**: `v=2` included in all v2 payloads

---

## 2. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                      Developer API                                │
│  DefaultMediaSourceFactory.setCmcdConfigurationFactory(factory)   │
└───────────────────────────────┬──────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│             CmcdConfigurationFactory (interface)                  │
│  Returns CmcdConfiguration (v1) | CmcdV2Configuration (v2) | null│
└──────────┬────────────────────────────────────┬──────────────────┘
           │                                    │
           ▼                                    ▼
┌───────────────────────┐          ┌────────────────────────────────┐
│  CmcdConfiguration    │          │     CmcdV2Configuration        │
│  (v1 — unchanged)     │          │  versionMode, eventTargets,    │
│                       │          │  heartbeatIntervalMs,           │
│                       │          │  useV1SyntaxForRequestMode      │
└───────────┬───────────┘          └──────────┬─────────────────────┘
            │                                  │
            ▼                                  ▼
┌───────────────────────┐          ┌────────────────────────────────┐
│   CmcdData.Factory    │          │      CmcdV2Data.Factory        │
│   (v1 — unchanged)    │          │  All v1 + v2 keys + inner list │
└───────────┬───────────┘          └──────────┬─────────────────────┘
            │                                  │
            └──────────────┬───────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                  CmcdSerializer / CmcdParser                     │
│        header mode | query parameter mode | body mode            │
└───────────────────────────────┬──────────────────────────────────┘
                                │
         ┌──────────────────────┼──────────────────────┐
         ▼                      ▼                      ▼
┌─────────────────┐  ┌──────────────────┐  ┌──────────────────────┐
│  DataSpec       │  │ CmcdEventReporter│  │ CmcdParser           │
│  (headers/query)│  │ (HTTP POST body) │  │ (round-trip testing) │
└─────────────────┘  └──────────────────┘  └──────────────────────┘
```

---

## 3. File Locations

### New V2 Files

| File | Purpose |
|------|---------|
| `libraries/exoplayer/src/main/java/…/upstream/CmcdConfigurationFactory.java` | Version-aware factory interface (supertype of `CmcdConfiguration.Factory`) |
| `libraries/exoplayer/src/main/java/…/upstream/CmcdV2Configuration.java` | V2 configuration: version mode, event targets, heartbeat interval |
| `libraries/exoplayer/src/main/java/…/upstream/CmcdV2Keys.java` | `@EventType`, `@StateType`, `@VersionMode` annotations + 31 new key constants + header group mappings |
| `libraries/exoplayer/src/main/java/…/upstream/CmcdV2Data.java` | V2 data assembly: Factory with all v1+v2 setters, `CmcdKeyValueStore` implementation, `addToDataSpec()` |
| `libraries/exoplayer/src/main/java/…/upstream/InnerListValue.java` | RFC 8941 inner list value type: `Item` inner class + `Builder` with type token validation |
| `libraries/exoplayer/src/main/java/…/upstream/EventTarget.java` | Event Mode endpoint configuration: URL, access token, allowed keys/event types |
| `libraries/exoplayer/src/main/java/…/upstream/CmcdSerializer.java` | Serialization to header, query, and body modes with inner list support |
| `libraries/exoplayer/src/main/java/…/upstream/CmcdParser.java` | Parsing from header, query, and body modes for round-trip verification |
| `libraries/exoplayer/src/main/java/…/upstream/CmcdKeyValueStore.java` | Interface for grouped CMCD key-value access (enables common serialization) |
| `libraries/exoplayer/src/main/java/…/upstream/CmcdEventReporter.java` | Event Mode: heartbeat scheduling + event dispatch + HTTP POST |

### Modified V1 Files

| File | Change |
|------|--------|
| `…/upstream/CmcdConfiguration.java` | Made non-final (to allow `CmcdV2Configuration` to extend); added `protected` constructor with `maxContentIdLength` param; `Factory` now extends `CmcdConfigurationFactory` |
| `…/source/DefaultMediaSourceFactory.java` | Added `setCmcdConfigurationFactory(CmcdConfigurationFactory)` overload; manages `CmcdEventReporter` lifecycle |
| `…/dash/DefaultDashChunkSource.java` | V2 branch: detects `CmcdV2Configuration`, uses `CmcdV2Data.Factory`, sequence number, low-latency detection |
| `…/hls/HlsChunkSource.java` | V2 branch: same pattern as DASH, low-latency HLS detection via `partTargetDurationUs` |
| `…/hls/HlsMediaChunk.java` | Extended `createInstance()` to accept `CmcdV2Data.Factory` + `CmcdV2Configuration` |
| `…/smoothstreaming/DefaultSsChunkSource.java` | V2 branch: same pattern, no low-latency detection (SS doesn't support it) |

### Test Files

| File | Purpose |
|------|---------|
| `…/upstream/CmcdV2NorNrrFormatTest.java` | V2 `nor` inner list format and `nrr` removal |
| `…/upstream/CmcdV2MixedVersionModeTest.java` | Mixed-version mode serialization dispatch |
| `…/upstream/CmcdBackwardCompatibilityTest.java` | V1 backward compatibility verification |

---

## 4. CmcdConfigurationFactory — The Entry Point

```java
@UnstableApi
public interface CmcdConfigurationFactory {
  @Nullable
  CmcdConfiguration createCmcdConfiguration(MediaItem mediaItem);
}
```

- Returns `CmcdConfiguration` → v1 behavior
- Returns `CmcdV2Configuration` → v2 behavior
- Returns `null` → CMCD disabled for that MediaItem

Backward compatibility: `CmcdConfiguration.Factory extends CmcdConfigurationFactory`, so all
existing v1 factories continue to work without modification.

---

## 5. CmcdV2Configuration — The V2 Configuration Layer

```java
@UnstableApi
public final class CmcdV2Configuration extends CmcdConfiguration {

  public static final int MAX_CONTENT_ID_LENGTH_V2 = 128;
  public static final int MODE_BODY = 2;
  public static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30_000;

  @CmcdV2Keys.VersionMode public final int versionMode;
  public final ImmutableList<EventTarget> eventTargets;
  public final long heartbeatIntervalMs;
  public final boolean useV1SyntaxForRequestMode;
}
```

### 5.1 Version Modes

| Mode | Constant | Request Mode | Event Mode |
|------|----------|--------------|------------|
| V2 Only | `VERSION_MODE_V2_ONLY` (0) | V2 syntax (`v=2`, inner list `nor`) | V2 syntax |
| Mixed | `VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT` (1) | V1 syntax (no `v`, scalar `nor` + `nrr`) | V2 syntax |

### 5.2 Builder

```java
CmcdV2Configuration config = new CmcdV2Configuration.Builder()
    .setSessionId("session-123")
    .setContentId("content-456")
    .setVersionMode(CmcdV2Keys.VERSION_MODE_V2_ONLY)
    .setEventTargets(ImmutableList.of(
        new EventTarget.Builder("https://analytics.example.com/cmcd")
            .setAccessToken("token-xyz")
            .setAllowedEventTypes(ImmutableSet.of(
                CmcdV2Keys.EVENT_TYPE_BITRATE_CHANGE,
                CmcdV2Keys.EVENT_TYPE_PLAY_STATE_CHANGE))
            .build()))
    .setHeartbeatIntervalMs(30_000)
    .build();
```

### 5.3 Content ID Validation

- V1 `CmcdConfiguration`: max 64 characters
- V2 `CmcdV2Configuration`: max 128 characters
- Exceeding the limit throws `IllegalArgumentException`

---

## 6. EventTarget — Event Mode Destination

```java
@UnstableApi
public final class EventTarget {
  public final String url;                           // Endpoint URL (required, non-empty)
  @Nullable public final String accessToken;         // Bearer token for Authorization header
  public final ImmutableSet<String> allowedKeys;     // Empty = all keys allowed
  public final ImmutableSet<String> allowedEventTypes; // Empty = all events allowed
}
```

Filtering behavior:
- **Key filtering**: Only keys in `allowedKeys` are included in the report. Empty set = all keys sent.
- **Event filtering**: Report is suppressed entirely if event type is not in `allowedEventTypes`. Empty set = all events sent.

---

## 7. CmcdV2Keys — Constants and Annotations

### 7.1 Type Annotations

| Annotation | Values | Usage |
|------------|--------|-------|
| `@EventType` | 19 tokens: `abs`, `abe`, `ae`, `as`, `b`, `bc`, `c`, `ce`, `e`, `h`, `m`, `pc`, `pe`, `pr`, `ps`, `rr`, `sk`, `t`, `um` | Event Mode event type |
| `@StateType` | 9 tokens: `s`, `p`, `k`, `r`, `a`, `e`, `f`, `q`, `d` | Player state for `sta` key |
| `@VersionMode` | `V2_ONLY` (0), `MIXED_V1_REQUEST_V2_EVENT` (1) | Version mode selection |

### 7.2 New Key Constants (31 keys)

| Category | Keys |
|----------|------|
| Event | `e` (event type), `sta` (state), `ts` (timestamp) |
| Quality | `dfa` (dropped frames), `ltc` (live latency), `msd` (media start delay), `pb` (playhead bitrate), `pt` (playhead time) |
| Buffer | `bsa` (starvation count), `bsd` (starvation duration), `bsda` (starvation duration accumulated) |
| Response | `cmsdd`, `cmsds`, `rc`, `smrt`, `ttfb`, `ttfbb`, `ttlb`, `url` |
| Session | `cen` (custom event name), `h` (hostname), `nr` (non rendered), `sn` (sequence number), `cs` (content signature) |
| Bitrate | `lab`, `lb`, `tab`, `tbl`, `tpb`, `ab` |
| Status | `bg` (backgrounded), `ec` (player error code) |

### 7.3 Header Group Assignments

| Header Group | New V2 Keys |
|--------------|-------------|
| CMCD-Object | `lab`, `lb`, `tab`, `tpb`, `ab` |
| CMCD-Request | `sta`, `dfa`, `pb`, `sn`, `cs`, `tbl` |
| CMCD-Session | `msd` |
| CMCD-Status | `pt`, `bsa`, `bsd`, `bsda`, `nr`, `ltc`, `bg`, `ec` |
| Event Only (N.A) | `e`, `ts`, `cmsdd`, `cmsds`, `rc`, `smrt`, `ttfb`, `ttfbb`, `ttlb`, `url`, `cen`, `h` |

### 7.4 New Stream Type and Format

```java
public static final String STREAM_TYPE_LOW_LATENCY_LIVE = "ll";
public static final String STREAMING_FORMAT_HESP = "e";
```

---

## 8. InnerListValue — RFC 8941 Inner List Syntax

```java
@UnstableApi
public final class InnerListValue {

  public static final class Item {
    public final Object value;            // Numeric or String
    @Nullable public final String typeToken;  // "v", "a", "av", or null
    @Nullable public final String rangeParam; // For 'nor' entries
  }

  public final ImmutableList<Item> items;

  public static final class Builder {
    public Builder addItem(Object value, @Nullable String typeToken);
    public Builder addItemWithRange(String value, @Nullable String range);
    public InnerListValue build();
  }
}
```

### 8.1 Serialization Format

```
br=(5000;v 320;a)          // bitrate: 5000 for video, 320 for audio
nor=("../seg1.mp4";r=0-500 "../seg2.mp4")  // next objects with range
bsa=(3;v 1;a)              // buffer starvation count per type
```

### 8.2 Type Token Validation

Valid tokens: `"v"` (video), `"a"` (audio), `"av"` (muxed), or `null`.
Invalid tokens throw `IllegalArgumentException` at build time.

### 8.3 Keys Supporting Inner List

`bl`, `br`, `mtp`, `nor`, `tb`, `lab`, `lb`, `tab`, `tbl`, `tpb`, `bsa`, `bsd`, `bsda`, `pb`, `ec`, `ab`

---

## 9. CmcdV2Data — The V2 Data Assembly Layer

### 9.1 Structure

`CmcdV2Data` implements `CmcdKeyValueStore` and groups keys into five maps:

```java
public interface CmcdKeyValueStore {
  ImmutableMap<String, Object> getObjectKeys();
  ImmutableMap<String, Object> getRequestKeys();
  ImmutableMap<String, Object> getSessionKeys();
  ImmutableMap<String, Object> getStatusKeys();
  ImmutableMap<String, Object> getEventOnlyKeys();
}
```

### 9.2 Factory Usage

```java
CmcdV2Data data = new CmcdV2Data.Factory(v2Config, CmcdData.STREAMING_FORMAT_DASH)
    // V1 compatible setters
    .setBitrate(5000)
    .setBufferLength(1800)
    .setPlaybackRate(1.0f)
    .setStreamType(CmcdData.STREAM_TYPE_LIVE)
    .setStartup(true)
    // V2 inner list setters (take priority over scalar when both set)
    .setBitratePerType(new InnerListValue.Builder()
        .addItem(5000, "v")
        .addItem(320, "a")
        .build())
    // V2 new key setters
    .setEventType(CmcdV2Keys.EVENT_TYPE_BITRATE_CHANGE)
    .setTimestampMs(System.currentTimeMillis())
    .setState(CmcdV2Keys.STATE_TYPE_PLAYING)
    .setSequenceNumber(42)
    .setDroppedFramesAccumulated(7)
    .setLiveLatencyMs(2500)
    .createCmcdData();
```

### 9.3 Key Rules

- **Unset keys are omitted**: No defaults or zero values in output
- **`v=2` auto-included**: Always present in session group
- **Inner list priority**: If both scalar and inner list variants are set, inner list wins
- **`nrr` omitted in v2**: When inner list `nor` is used, range is embedded as item parameter

### 9.4 DataSpec Attachment

```java
DataSpec result = data.addToDataSpec(dataSpec, v2Configuration);
```

In mixed-version mode, `addToDataSpec()` creates a v1 "request view" that:
- Omits the `v` key from session group
- Converts inner list `nor` to scalar format with separate `nrr`

---

## 10. CmcdSerializer — Serialization Layer

```java
@UnstableApi
public final class CmcdSerializer {
  public static ImmutableMap<String, String> toHeaders(CmcdKeyValueStore data);
  public static String toQueryParameter(CmcdKeyValueStore data);
  public static String toRawQueryParameter(CmcdKeyValueStore data);
  public static String toBody(CmcdKeyValueStore data);
  static String formatValue(String key, Object value);
  static String formatInnerList(InnerListValue innerList);
}
```

### 10.1 Value Formatting Rules

| Type | Format | Example |
|------|--------|---------|
| Integer/Long | bare number | `br=5000` |
| Boolean (true) | key only | `su` |
| String | double-quoted | `sid="abc"` |
| Float/Double | 2 decimal places | `pr=1.50` |
| InnerListValue | RFC 8941 inner list | `br=(5000;v 320;a)` |
| Token (other) | bare value | `st=l` |

### 10.2 Body Mode Format

- Records are comma-separated key=value pairs (query-argument format)
- Multiple records separated by LF (0x0A)
- **No** URL encoding
- **No** header group prefixes
- Empty records omitted
- Single record has no trailing LF

### 10.3 Key Ordering

Keys are sorted alphabetically within each header group line (header mode) or across all keys
(query parameter and body modes).

---

## 11. CmcdParser — Round-Trip Verification

```java
@UnstableApi
public final class CmcdParser {
  public static ImmutableMap<String, Object> fromHeaders(ImmutableMap<String, String> headers);
  public static ImmutableMap<String, Object> fromQueryParameter(String queryValue);
  public static ImmutableMap<String, Object> fromBody(String body);
  static InnerListValue parseInnerList(String innerListStr);
}
```

Parsing rules (inverse of serialization):
- Key only (no `=`) → Boolean `true`
- Bare integer → Integer or Long
- Double-quoted → String (quotes removed)
- Decimal number → Float
- Value starting with `(` → InnerListValue
- Other → String (opaque token)

Malformed input is handled gracefully: individual pair failures are logged and skipped.

---

## 12. CmcdEventReporter — Event Mode

### 12.1 Lifecycle

```java
CmcdEventReporter reporter = new CmcdEventReporter(v2Config, handler, listener);
reporter.start();   // Schedule heartbeat at configured interval
// ... playback ...
reporter.stop();    // Cancel heartbeat, shutdown HTTP executor
```

Created by `DefaultMediaSourceFactory` when:
- Configuration factory returns a `CmcdV2Configuration`
- That configuration has non-empty `eventTargets`

Stopped on media item change or source release.

### 12.2 Reporting Methods

```java
// Event-triggered report
reporter.reportEvent(CmcdV2Keys.EVENT_TYPE_BITRATE_CHANGE, cmcdV2Data);

// Heartbeat (uses event type "t" — TIME_INTERVAL)
reporter.reportHeartbeat(cmcdV2Data);
```

### 12.3 Report Content

Every event report includes:
- `e` key (event type token)
- `ts` key (timestamp in milliseconds)

These are ensured even if not explicitly set on the data.

### 12.4 HTTP POST Details

- Method: `POST`
- Content-Type: `application/cmcd`
- Body: `CmcdSerializer.toBody(filteredData)` (LF-separated, no URL encoding)
- Authorization: `Bearer <token>` when `EventTarget.accessToken` is set
- Timeout: 10s connect, 10s read
- Failures: logged via `Listener.onEventReportFailed()`, never interrupt playback

### 12.5 Per-Target Filtering

For each event target:
1. **Event type filter**: If `allowedEventTypes` is non-empty and doesn't contain the event type, the entire report is suppressed for that target
2. **Key filter**: If `allowedKeys` is non-empty, only keys in the intersection of {populated keys} ∩ {allowed keys} are included

---

## 13. Integration Points per Streaming Format

### 13.1 Version Detection Pattern

All chunk sources use the same pattern:

```java
if (cmcdConfiguration instanceof CmcdV2Configuration) {
    CmcdV2Configuration v2Config = (CmcdV2Configuration) cmcdConfiguration;
    CmcdV2Data.Factory cmcdV2DataFactory =
        new CmcdV2Data.Factory(v2Config, streamingFormat);
    // ... populate fields ...
    cmcdV2DataFactory.setSequenceNumber(sequenceNumber++);
    CmcdV2Data cmcdV2Data = cmcdV2DataFactory.createCmcdData();
    dataSpec = cmcdV2Data.addToDataSpec(dataSpec, v2Config);
} else {
    // Existing v1 path — unchanged
    CmcdData.Factory cmcdDataFactory = new CmcdData.Factory(cmcdConfiguration, streamingFormat);
    // ...
}
```

### 13.2 DASH (`DefaultDashChunkSource`)

- **Sequence number**: Monotonically increasing `long`, incremented per request
- **Low-latency detection**: `manifest.serviceDescription != null && targetOffsetMs != C.TIME_UNSET`
- **Stream type**: `ll` for low-latency, `l` for standard live, `v` for VOD

### 13.3 HLS (`HlsChunkSource`)

- **Sequence number**: Same pattern as DASH
- **Low-latency detection**: `playlist.partTargetDurationUs != C.TIME_UNSET`
- **Stream type**: `ll` for low-latency HLS, `l` for standard live, `v` for VOD
- **Encryption keys**: V2 data attached to AES key requests as well

### 13.4 SmoothStreaming (`DefaultSsChunkSource`)

- **Sequence number**: Same pattern
- **No low-latency**: SmoothStreaming has no low-latency variant
- **Stream type**: `l` for live, `v` for VOD

---

## 14. Mixed-Version Mode

### 14.1 When to Use

Mixed mode enables Event Mode analytics (v2) while maintaining CDN compatibility for
request-attached data (v1). This avoids CDN disruption when CDN parsers don't yet support
v2 inner list syntax.

### 14.2 Behavior

| Aspect | Request Mode (attached to media requests) | Event Mode (HTTP POST to targets) |
|--------|------------------------------------------|----------------------------------|
| `v` key | Omitted | `v=2` |
| `nor` format | Single quoted string (v1) | Inner list (v2) |
| `nrr` key | Present (v1) | Omitted (v2) |
| Inner list keys | Not used | Used |

### 14.3 Implementation

`CmcdV2Data.addToDataSpec()` internally creates a "v1 request view" when
`useV1SyntaxForRequestMode` is true:
- Filters `v` key from session group
- Converts inner list `nor` to scalar format
- Extracts range from first inner list item into separate `nrr` key

---

## 15. `nor` Key Format Changes (V1 → V2)

| Mode | `nor` Format | `nrr` Key |
|------|-------------|-----------|
| V1 | `nor="../seg.mp4"` (single quoted string) | `nrr="0-1024"` (separate key) |
| V2 | `nor=("../seg1.mp4";r=0-500 "../seg2.mp4")` (inner list) | Not present |

In v2, range is embedded as `;r=range` parameter within each inner list item.

---

## 16. Configuration API (Developer-Facing)

### 16.1 V2 Only Mode (Simplest)

```java
CmcdConfigurationFactory factory = mediaItem ->
    new CmcdV2Configuration.Builder()
        .setSessionId(UUID.randomUUID().toString())
        .setContentId(mediaItem.mediaId)
        .build();

ExoPlayer player = new ExoPlayer.Builder(context)
    .setMediaSourceFactory(
        new DefaultMediaSourceFactory(context)
            .setCmcdConfigurationFactory(factory))
    .build();
```

### 16.2 V2 with Event Mode

```java
CmcdConfigurationFactory factory = mediaItem ->
    new CmcdV2Configuration.Builder()
        .setSessionId(UUID.randomUUID().toString())
        .setContentId(mediaItem.mediaId)
        .setVersionMode(CmcdV2Keys.VERSION_MODE_V2_ONLY)
        .setEventTargets(ImmutableList.of(
            new EventTarget.Builder("https://analytics.example.com/cmcd")
                .setAccessToken("my-api-key")
                .setAllowedKeys(ImmutableSet.of("br", "bl", "bs", "e", "ts"))
                .setAllowedEventTypes(ImmutableSet.of(
                    CmcdV2Keys.EVENT_TYPE_BITRATE_CHANGE,
                    CmcdV2Keys.EVENT_TYPE_PLAY_STATE_CHANGE,
                    CmcdV2Keys.EVENT_TYPE_TIME_INTERVAL))
                .build()))
        .setHeartbeatIntervalMs(30_000)
        .build();
```

### 16.3 Mixed-Version Mode (CDN Compatibility)

```java
CmcdConfigurationFactory factory = mediaItem ->
    new CmcdV2Configuration.Builder()
        .setSessionId(UUID.randomUUID().toString())
        .setContentId(mediaItem.mediaId)
        .setVersionMode(CmcdV2Keys.VERSION_MODE_MIXED_V1_REQUEST_V2_EVENT)
        .setEventTargets(ImmutableList.of(
            new EventTarget.Builder("https://analytics.example.com/cmcd").build()))
        .build();
```

### 16.4 Disabling CMCD per MediaItem

```java
CmcdConfigurationFactory factory = mediaItem -> {
    if (shouldDisable(mediaItem)) return null;
    return new CmcdV2Configuration.Builder()
        .setContentId(mediaItem.mediaId)
        .build();
};
```

### 16.5 Backward Compatible (V1 Still Works)

```java
// Existing v1 code — no changes needed
new DefaultMediaSourceFactory(context)
    .setCmcdConfigurationFactory(CmcdConfiguration.Factory.DEFAULT);
```

---

## 17. Output Format Examples

### 17.1 V2 Header Mode (DASH, live, inner lists)

```http
GET /segment_42.m4s HTTP/1.1
CMCD-Object: br=(5000;v 320;a),d=4000,ot=av,tb=(8000;v 500;a)
CMCD-Request: bl=(2200;v 3100;a),mtp=(6200;v 450;a),sn=42,sta=p
CMCD-Session: cid="content-1",sf=d,sid="session-1",st=l,v=2
CMCD-Status: pt=125000
```

### 17.2 V2 Body Mode (Event Mode POST)

```http
POST /cmcd HTTP/1.1
Content-Type: application/cmcd
Authorization: Bearer my-api-key

br=(5000;v 320;a),bs,cid="content-1",e="bc",sf=d,sid="session-1",st=l,ts=1700000000000,v=2
```

### 17.3 Mixed Mode — Request Headers (V1 Syntax)

```http
GET /segment_42.m4s HTTP/1.1
CMCD-Object: br=5000,d=4000,ot=v,tb=8000
CMCD-Request: bl=2200,mtp=6200,nor="../seg43.m4s",nrr="0-51200",sn=42
CMCD-Session: cid="content-1",sf=d,sid="session-1",st=l
CMCD-Status: rtp=10000
```

Note: No `v` key, scalar `nor`, `nrr` present.

### 17.4 Mixed Mode — Event POST (V2 Syntax)

Same as 17.2 — Event Mode always uses v2 syntax regardless of mixed mode setting.

---

## 18. Backward Compatibility Guarantees

| Guarantee | How |
|-----------|-----|
| V1 API unchanged | `CmcdConfiguration`, `CmcdConfiguration.Factory`, `CmcdData`, `CmcdData.Factory` — no methods removed or renamed |
| V1 output unchanged | No `v` key, scalar `nor`/`nrr`, same formatting rules |
| V1 Factory still works | `CmcdConfiguration.Factory extends CmcdConfigurationFactory` — existing lambdas/implementations work |
| V1 content ID limit | 64 chars max enforced in `CmcdConfiguration` constructor |
| No new dependencies on v1 path | V2 classes only loaded when `instanceof CmcdV2Configuration` is true |

---

## 19. Dependency Graph

```
CmcdConfigurationFactory (interface, no deps)
    ↑
CmcdConfiguration.Factory (extends CmcdConfigurationFactory)
    ↓
CmcdConfiguration ←── CmcdV2Configuration
                          ↓
                       EventTarget
                          ↓
CmcdV2Keys (constants)   CmcdV2Data ──→ CmcdKeyValueStore
    ↓                       ↓                    ↓
InnerListValue         CmcdSerializer ←─── CmcdParser
                          ↓
                    CmcdEventReporter
                          ↓
                   DefaultMediaSourceFactory
                          ↓
            DefaultDashChunkSource / HlsChunkSource / DefaultSsChunkSource
```

External dependencies: Guava (`ImmutableList`, `ImmutableMap`, `ImmutableSet`), Android SDK
(`Uri`, `Handler`, `Log`), Java net (`HttpURLConnection`).

---

## 20. Testing Strategy

### 20.1 Unit Tests

- `CmcdV2NorNrrFormatTest` — Inner list `nor` format, `nrr` omission, v1 scalar format preservation
- `CmcdV2MixedVersionModeTest` — Mixed mode dispatch: v1 request view, v2 event view, DataSpec attachment
- `CmcdBackwardCompatibilityTest` — V1 output unchanged, no `v` key, header/query modes, body format

### 20.2 Property-Based Tests (Design)

The spec defines 16 correctness properties validatable via property-based testing:

| Property | What it verifies |
|----------|-----------------|
| P1: Round-Trip | Serialize → parse → equivalent data |
| P2: V1 Invariant | V1 output never contains `v` key |
| P3: V2 Version | V2 output always contains `v=2` |
| P4: Content ID Length | V1 ≤ 64, V2 ≤ 128 |
| P5: Event Metadata | Every event has `e` and `ts` |
| P6: Key Filtering | Report contains only intersection of populated ∩ allowed |
| P7: Event Filtering | Report sent iff target allows event type |
| P8: Body Format | LF-separated, no URL encoding, no header prefixes |
| P9: Inner List | Multi-item → RFC 8941 syntax |
| P10: V2 `nor` | Inner list format, no `nrr` |
| P11: Key-to-Group | Each key in correct header group |
| P12: Mixed Mode | Request = v1, Event = v2 |
| P13: Key Omission | Unset keys never in output |
| P14: Alphabetical | Keys sorted within groups |
| P15: Value Types | Correct formatting per type |
| P16: Stream Type/Format | `st=ll` and `sf=e` emitted correctly |

---

## 21. Data Flow Example (V2 DASH Media Chunk)

1. `ExoPlayer` creates `DefaultMediaSourceFactory` with a `CmcdConfigurationFactory`.
2. `DefaultMediaSourceFactory.createMediaSource()` calls `manageCmcdEventReporter(mediaItem)`:
   - Stops any existing reporter
   - Calls `factory.createCmcdConfiguration(mediaItem)`
   - If result is `CmcdV2Configuration` with event targets → creates and starts `CmcdEventReporter`
3. `DashMediaSource` receives the configuration and passes it to `DefaultDashChunkSource`.
4. In `getNextChunk()`:
   a. Detects `CmcdV2Configuration` via `instanceof`
   b. Creates `CmcdV2Data.Factory` with streaming format `"d"`
   c. Sets bitrate, buffer length, throughput, startup, stream type from track selection
   d. Detects low-latency via `ServiceDescriptionElement.targetOffsetMs`
   e. Sets `sequenceNumber++`
   f. `createCmcdData()` builds immutable `CmcdV2Data` with `v=2` in session group
5. Chunk constructor calls `cmcdV2Data.addToDataSpec(dataSpec, v2Config)`:
   - If mixed mode: creates v1 request view (no `v`, scalar `nor`/`nrr`)
   - Serializes via `CmcdSerializer.toHeaders()` or `toQueryParameter()`
   - Returns modified DataSpec
6. The DataSource sends the request with CMCD v2 data attached.
7. Meanwhile, `CmcdEventReporter` sends heartbeats every 30s and event reports on triggers.

---

## 22. Sequence Number (`sn`) Behavior

- **Type**: Monotonically increasing `long` starting at 0
- **Scope**: Per chunk source instance (tied to a session)
- **Reset**: New value when a new `CmcdConfiguration` / session is created (new chunk source)
- **Purpose**: Tracks the sequence of CMCD reports to a target within a session
- **Note**: This is NOT a segment number — it counts CMCD report transmissions

---

## 23. Buffer Starvation Keys

The buffer starvation keys use `InnerListValue` type (not scalar `long`):

```java
// Per-type buffer starvation count
factory.setBufferStarvationCount(new InnerListValue.Builder()
    .addItem(3, "v")
    .addItem(1, "a")
    .build());
```

This applies to: `bsa`, `bsd`, `bsda`

---

## 24. Body Transmission Mode Restrictions

- Body mode (`MODE_BODY = 2`) is **only valid for Event Mode**
- Request Mode (chunk source data) uses header mode or query parameter mode only
- The `CmcdEventReporter` always uses body mode for its HTTP POST requests
- Batch object data transfer (multiple media objects in one body) is explicitly NOT supported

