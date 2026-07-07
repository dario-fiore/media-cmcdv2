# Implementation Plan: CMCD V2 Support

## Overview

This plan implements CTA-5004-B (CMCD v2) support in AndroidX Media3 / ExoPlayer by building from foundational types and constants upward through serialization, data assembly, configuration, event reporting, and chunk source integration. Each task incrementally builds on previous outputs, ensuring no orphaned code.

## Tasks

- [x] 1. Define V2 constants, type annotations, and foundational value types
  - [x] 1.1 Create type annotations and constant definitions
    - Create `@EventType` StringDef annotation with all 19 CTA-5004-B event type tokens: `abs` (ad break start), `abe` (ad break end), `ae` (ad end), `as` (ad start), `b` (backgrounded), `bc` (bitrate change), `c` (content id change), `ce` (custom event), `e` (error), `h` (hostname change), `m` (mute), `pc` (player collapse), `pe` (player expand), `pr` (playback rate change), `ps` (play state change), `rr` (response received), `sk` (skip), `t` (time interval), `um` (unmute)
    - Create `@StateType` StringDef annotation with tokens: `s` (starting), `p` (playing), `k` (seeking), `r` (rebuffering), `a` (paused), `e` (ended), `f` (fatal error), `q` (quit), `d` (preloading)
    - Create `@VersionMode` IntDef annotation with constants: `V2_ONLY = 0`, `MIXED_V1_REQUEST_V2_EVENT = 1`
    - Add new key constants to `CmcdData` or a new `CmcdV2Keys` class for all CTA-5004-B reserved keys: `KEY_EVENT_TYPE` (`e`), `KEY_STATE` (`sta`), `KEY_TIMESTAMP` (`ts`), `KEY_DROPPED_FRAMES_ACCUMULATED` (`dfa`), `KEY_LIVE_LATENCY` (`ltc`), `KEY_MEDIA_START_DELAY` (`msd`), `KEY_PLAYHEAD_BITRATE` (`pb`), `KEY_PLAYHEAD_TIME` (`pt`), `KEY_BUFFER_STARVATION_COUNT` (`bsa`), `KEY_BUFFER_STARVATION_DURATION` (`bsd`), `KEY_BUFFER_STARVATION_DURATION_ACCUMULATED` (`bsda`), `KEY_CMSD_DYNAMIC_HEADER` (`cmsdd`), `KEY_CMSD_STATIC_HEADER` (`cmsds`), `KEY_RESPONSE_CODE` (`rc`), `KEY_SMRT_DATA_HEADER` (`smrt`), `KEY_TIME_TO_FIRST_BYTE` (`ttfb`), `KEY_TIME_TO_FIRST_BODY_BYTE` (`ttfbb`), `KEY_TIME_TO_LAST_BYTE` (`ttlb`), `KEY_REQUESTED_URL` (`url`), `KEY_CUSTOM_EVENT_NAME` (`cen`), `KEY_HOSTNAME` (`h`), `KEY_NON_RENDERED` (`nr`), `KEY_SEQUENCE_NUMBER` (`sn`), `KEY_CONTENT_SIGNATURE` (`cs`), `KEY_LOWEST_AGGREGATED_BITRATE` (`lab`), `KEY_LOWEST_ENCODED_BITRATE` (`lb`), `KEY_TOP_AGGREGATED_BITRATE` (`tab`), `KEY_TARGET_BUFFER_LENGTH` (`tbl`), `KEY_TOP_PLAYABLE_BITRATE` (`tpb`), `KEY_AGGREGATE_ENCODED_BITRATE` (`ab`), `KEY_BACKGROUNDED` (`bg`), `KEY_PLAYER_ERROR_CODE` (`ec`)
    - Add `STREAM_TYPE_LOW_LATENCY_LIVE = "ll"` and `STREAMING_FORMAT_HESP = "e"` constants
    - Define header group assignment mapping for each new key per the design table:
      - CMCD-Object: `lab`, `lb`, `tab`, `tpb`, `ab`
      - CMCD-Request: `sta`, `dfa`, `pb`, `sn`, `cs`, `tbl`
      - CMCD-Session: `msd`
      - CMCD-Status: `pt`, `bsa`, `bsd`, `bsda`, `nr`, `ltc`, `bg`, `ec`
      - Event only (N.A): `e`, `ts`, `cmsdd`, `cmsds`, `rc`, `smrt`, `ttfb`, `ttfbb`, `ttlb`, `url`, `cen`, `h`
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 9.1, 9.2_

  - [x] 1.2 Implement `InnerListValue` class
    - Create `InnerListValue` with `Item` inner class (value, typeToken, rangeParam fields)
    - Implement `InnerListValue.Builder` with `addItem(Object value, String typeToken)` and `addItemWithRange(String value, String range)` methods
    - Validate type tokens (`v`, `a`, `av`) in builder
    - Implement `build()` with `IllegalArgumentException` for invalid type tokens
    - _Requirements: 5.1, 5.2, 5.4_

  - [x]* 1.3 Write unit tests for `InnerListValue`
    - Test builder with valid items and type tokens
    - Test single-item creation
    - Test rejection of invalid type tokens
    - Test immutability of built instance
    - _Requirements: 5.1, 5.4_

- [x] 2. Implement serialization and parsing layer
  - [x] 2.1 Implement `CmcdSerializer`
    - Implement `toHeaders(CmcdKeyValueStore data)` returning map of header group name to formatted key-value string
    - Implement `toQueryParameter(CmcdKeyValueStore data)` returning URL-encoded single string
    - Implement `toBody(CmcdKeyValueStore data)` returning LF-separated CMCD records where each record is comma-separated key=value pairs in query-argument format (NOT URL-encoded, NO header group prefixes). If only a single record is present, no trailing LF is included.
    - Implement `formatValue(String key, Object value)` with rules: integers as bare numbers, boolean true as key-only, strings as double-quoted, floats with 2 decimal places
    - Implement `formatInnerList(InnerListValue innerList)` with RFC 8941 inner list syntax
    - Enforce alphabetical key ordering within each header group line
    - Omit records with no populated keys from body output
    - _Requirements: 4.1, 4.3, 4.4, 4.5, 5.2, 15.1, 15.2, 15.3, 15.4_

  - [x] 2.2 Implement `CmcdParser`
    - Implement `fromHeaders(ImmutableMap<String, String> headers)` parsing header-mode strings
    - Implement `fromQueryParameter(String queryValue)` parsing query-parameter-mode strings
    - Implement `fromBody(String body)` parsing body-mode strings (LF-separated records in query-argument format)
    - Implement `parseInnerList(String innerListStr)` reconstructing `InnerListValue` from RFC 8941 syntax
    - Handle malformed input gracefully: return partial parse, log warnings, preserve unknown keys as opaque strings
    - _Requirements: 16.1, 16.2, 16.3, 16.4_

  - [x]* 2.3 Write property test: Serialization/Parsing Round-Trip (Property 1)
    - **Property 1: Serialization/Parsing Round-Trip**
    - Generate random CmcdV2Data objects with arbitrary key subsets and value types
    - Verify: serialize to header mode then parse back produces equivalent key-value map
    - Verify: serialize to query parameter mode then parse back produces equivalent key-value map
    - Verify: serialize to body mode then parse back produces equivalent key-value map
    - **Validates: Requirements 15.5, 16.1, 16.2, 16.3, 16.4, 16.5**

  - [x]* 2.4 Write property test: Inner List Serialization (Property 9)
    - **Property 9: Inner List Serialization**
    - Generate random InnerListValues with multiple items for keys `bl`, `br`, `mtp`, `nor`, `tb`, `lab`, `lb`, `tab`, `tbl`, `tpb`, `bsa`, `bsd`, `bsda`, `pb`, `ec`, `ab`
    - Verify: multi-item lists produce RFC 8941 inner list syntax `key=(value1;type1 value2;type2)`
    - Verify: single-item with no type token produces standard scalar format
    - **Validates: Requirements 5.1, 5.2, 5.3, 15.1**

  - [x]* 2.5 Write property test: Alphabetical Key Ordering (Property 14)
    - **Property 14: Alphabetical Key Ordering**
    - Generate random subsets of populated keys within a header group
    - Verify: serialized output lists keys in lexicographic order
    - **Validates: Requirements 15.2**

  - [x]* 2.6 Write property test: Value Type Formatting (Property 15)
    - **Property 15: Value Type Formatting**
    - Generate random integers, booleans, strings, and floats
    - Verify: integers serialize as bare numbers, boolean true as key-only, strings as double-quoted, floats with 2 decimal places
    - **Validates: Requirements 15.3**

  - [x]* 2.7 Write unit tests for `CmcdSerializer` and `CmcdParser`
    - Test specific format examples for header, query, and body modes
    - Test body mode output: LF-separated records without URL encoding or header group prefixes
    - Test inner list formatting with known inputs
    - Test empty record omission in body output
    - Test single-record body has no trailing LF
    - Test parser error recovery with malformed input
    - _Requirements: 4.1, 4.3, 4.4, 4.5, 15.1, 15.2, 15.3, 15.4, 16.1, 16.2, 16.3_

- [x] 3. Checkpoint - Ensure serialization layer tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement configuration layer
  - [x] 4.1 Create `CmcdConfigurationFactory` interface
    - Define interface with `createCmcdConfiguration(MediaItem mediaItem)` returning nullable `CmcdConfiguration`
    - Annotate with `@UnstableApi`
    - Ensure existing `CmcdConfiguration.Factory` extends `CmcdConfigurationFactory` for backward compatibility
    - _Requirements: 1.1, 1.5, 2.1_

  - [x] 4.2 Implement `EventTarget` class
    - Implement `EventTarget` with fields: `url`, `accessToken`, `allowedKeys`, `allowedEventTypes`
    - Implement `EventTarget.Builder` with validation (empty URL throws `IllegalArgumentException`)
    - Use `ImmutableSet` for allowed keys and event types (empty set = all allowed)
    - _Requirements: 3.2, 3.8, 11.1, 11.2, 12.1_

  - [x] 4.3 Implement `CmcdV2Configuration` class
    - Extend `CmcdConfiguration` with v2-specific fields: `versionMode`, `eventTargets`, `heartbeatIntervalMs`, `useV1SyntaxForRequestMode`
    - Define `MAX_CONTENT_ID_LENGTH_V2 = 128` and `MODE_BODY = 2`
    - Implement Builder with content ID length validation (max 128 chars, throw `IllegalArgumentException` if exceeded)
    - Retain v1 content ID validation (64 chars max) in parent class unchanged
    - Default heartbeat interval of 30 seconds per CTA-5004-B spec
    - _Requirements: 1.3, 1.4, 3.9, 4.2, 8.1, 8.2, 8.3, 10.1_

  - [x]* 4.4 Write property test: Content ID Length Validation (Property 4)
    - **Property 4: Content ID Length Validation**
    - Generate random strings of varying lengths
    - Verify: v1 CmcdConfiguration accepts string iff length <= 64, rejects with IllegalArgumentException otherwise
    - Verify: CmcdV2Configuration accepts string iff length <= 128, rejects with IllegalArgumentException otherwise
    - **Validates: Requirements 2.5, 8.1, 8.2, 8.3**

  - [x]* 4.5 Write unit tests for configuration classes
    - Test `CmcdConfigurationFactory` returns correct types per MediaItem
    - Test `EventTarget` builder validation
    - Test `CmcdV2Configuration` builder with all combinations of version mode
    - Test null return disables CMCD
    - Test default heartbeat interval is 30 seconds
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 3.2, 3.9, 12.1_

- [x] 5. Implement data assembly layer
  - [x] 5.1 Implement `CmcdV2Data` and `CmcdV2Data.Factory`
    - Create `CmcdV2Data` holding v2 key groups: event, quality, buffer, response, session extension, bitrate, status
    - Implement `CmcdV2Data.Factory` with all v1 setter delegation plus new v2 key setters:
      - Event setters: `setEventType(@EventType String)`, `setTimestampMs(long)`, `setState(@StateType String)`
      - Quality setters: `setDroppedFramesAccumulated(long)`, `setLiveLatencyMs(long)`, `setMediaStartDelayMs(long)`, `setPlayheadBitratePerType(InnerListValue)`, `setPlayheadTimeMs(long)`
      - Buffer setters: `setBufferStarvationCount(InnerListValue)`, `setBufferStarvationDuration(InnerListValue)`, `setBufferStarvationDurationAccumulated(InnerListValue)` — all use InnerListValue type
      - Response setters: `setTimeToFirstByteMs(long)`, `setTimeToFirstBodyByteMs(long)`, `setTimeToLastByteMs(long)`, `setResponseCode(int)`, `setSmrtDataHeader(String)`, `setCmsdDynamicHeader(String)`, `setCmsdStaticHeader(String)`, `setRequestedUrl(String)`
      - Session extension setters: `setCustomEventName(String)`, `setHostname(String)`, `setNonRendered(boolean)`, `setSequenceNumber(long)`, `setContentSignature(String)`
      - Bitrate setters: `setLowestAggregatedBitratePerType(InnerListValue)`, `setLowestEncodedBitratePerType(InnerListValue)`, `setTopAggregatedBitratePerType(InnerListValue)`, `setTargetBufferLengthPerType(InnerListValue)`, `setTopPlayableBitratePerType(InnerListValue)`, `setAggregateEncodedBitratePerType(InnerListValue)`
      - Status setters: `setBackgrounded(boolean)`, `setPlayerErrorCode(InnerListValue)`
    - Implement inner list setters for v1 keys promoted to inner list in v2: `setBitratePerType`, `setBufferLengthPerType`, `setMeasuredThroughputPerType`, `setTopBitratePerType`, `setNextObjectRequestList`
    - Automatically include `v=2` in session group when creating v2 data
    - Implement `createCmcdData()` producing immutable `CmcdV2Data`
    - Ensure unset keys are omitted from output (no defaults or zeros)
    - _Requirements: 1.3, 5.1, 7.8, 13.1, 13.2, 13.5_

  - [x] 5.2 Implement v2 `nor` key formatting and `nrr` removal
    - Format `nor` as inner list of strings in v2 mode with range as item parameter
    - Omit `nrr` key entirely from v2 payloads
    - Retain v1 `nor` (single quoted string) and `nrr` (separate key) formatting when v1 syntax is active
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 5.3 Implement mixed-version mode serialization dispatch
    - When `useV1SyntaxForRequestMode` is true, serialize Request Mode data without `v` key and with v1 `nor`/`nrr` format
    - Serialize Event Mode data with `v=2` and v2 inner list `nor` format
    - Wire `CmcdV2Data.addToDataSpec()` to route to correct serialization path based on version mode
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [x]* 5.4 Write property test: V2 Version Key Inclusion (Property 3)
    - **Property 3: V2 Version Key Inclusion**
    - Generate random CmcdV2Data objects serialized in v2 mode (not mixed-mode request path)
    - Verify: output always contains `v=2` in session group
    - **Validates: Requirements 1.3, 13.2**

  - [x]* 5.5 Write property test: V1 Serialization Invariant (Property 2)
    - **Property 2: V1 Serialization Invariant**
    - Generate random CmcdData objects via v1 CmcdData.Factory
    - Verify: output never contains `v` key
    - Verify: `nor` formatted as single quoted string
    - Verify: `nrr` included as separate key when range data present
    - **Validates: Requirements 1.2, 2.2, 2.4, 6.3**

  - [x]* 5.6 Write property test: V2 'nor' Key Format (Property 10)
    - **Property 10: V2 'nor' Key Format**
    - Generate random CmcdV2Data with next-object-request data in v2 mode
    - Verify: `nor` formatted as inner list of strings, range as item parameter
    - Verify: `nrr` key never appears in v2 output
    - **Validates: Requirements 6.1, 6.2, 6.4**

  - [x] 5.7 Write property test: Mixed Mode Version Syntax (Property 12)
    - **Property 12: Mixed Mode Version Syntax**
    - Generate random CmcdV2Data in mixed-version mode
    - Verify: Request Mode output omits `v` key, uses v1 `nor`/`nrr`
    - Verify: Event Mode output includes `v=2`, uses v2 inner list `nor`, omits `nrr`
    - **Validates: Requirements 10.2, 10.3, 10.4**

  - [x]* 5.8 Write property test: Key Omission for Unset Values (Property 13)
    - **Property 13: Key Omission for Unset Values**
    - Generate random subsets of keys that are NOT set on factory
    - Verify: unset keys never appear in serialized output
    - **Validates: Requirements 13.5**

  - [x]* 5.9 Write property test: Key-to-Header-Group Assignment (Property 11)
    - **Property 11: Key-to-Header-Group Assignment**
    - Generate random CmcdV2Data with various keys populated, serialize in header mode
    - Verify: each key appears in its correct assigned header group and no other
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8**

  - [x]* 5.10 Write property test: Stream Type and Format Emission (Property 16)
    - **Property 16: Stream Type and Format Emission**
    - Generate CmcdV2Data with stream type set to low-latency LIVE or format set to HESP
    - Verify: output contains `st=ll` when low-latency LIVE, `sf=e` when HESP
    - **Validates: Requirements 9.3, 9.4**

- [x] 6. Checkpoint - Ensure data assembly and property tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement Event Mode reporting
  - [x] 7.1 Implement `CmcdEventReporter`
    - Create `CmcdEventReporter` with constructor taking `CmcdV2Configuration`, `Handler`, and `Listener`
    - Implement `start()` to schedule heartbeat messages at configured interval (default 30 seconds per CTA-5004-B)
    - Implement `stop()` to cancel pending heartbeat messages and clean up
    - Implement `reportEvent(@EventType String eventType, CmcdV2Data data)` dispatching to applicable targets using the full set of 19 event type tokens (abs, abe, ae, as, b, bc, c, ce, e, h, m, pc, pe, pr, ps, rr, sk, t, um)
    - Implement `reportHeartbeat(CmcdV2Data data)` sending periodic reports
    - Include `e` (event type) and `ts` (timestamp) keys in all event reports
    - Apply per-target key filtering: include only keys in target's `allowedKeys` set (all keys if set is empty)
    - Apply per-target event filtering: suppress report if event type not in target's `allowedEventTypes` (send all if set is empty)
    - Send HTTP POST with body in LF-separated query-argument format (comma-separated key=value, NOT URL-encoded, NO header group prefixes) and `Content-Type: application/cmcd`
    - Include `Authorization` header with access token when configured on target
    - Handle HTTP failures gracefully: log via `Listener.onEventReportFailed()`, do not interrupt playback
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 4.1, 4.2, 4.3, 4.5, 11.3, 11.4, 11.5, 12.2, 12.3_

  - [x]* 7.2 Write property test: Event Report Required Metadata (Property 5)
    - **Property 5: Event Report Required Metadata**
    - Generate random event reports from CmcdEventReporter
    - Verify: every serialized event payload contains both `e` and `ts` keys
    - **Validates: Requirements 3.5, 3.6**

  - [x]* 7.3 Write property test: Key Filtering (Property 6)
    - **Property 6: Key Filtering**
    - Generate random populated key sets and random EventTarget allowed-key sets
    - Verify: report contains only intersection of populated keys and allowed keys
    - Verify: when allowed-key set is empty, all populated keys are included
    - **Validates: Requirements 3.8, 11.3, 11.5**

  - [x]* 7.4 Write property test: Event Type Filtering and Dispatch (Property 7)
    - **Property 7: Event Type Filtering and Dispatch**
    - Generate random event types and random EventTarget allowed-event-type sets
    - Verify: report sent iff target's allowed set is empty or contains event type
    - Verify: report suppressed when event type not in target's allowed set
    - **Validates: Requirements 3.4, 11.4**

  - [x]* 7.5 Write property test: Body Format Structure (Property 8)
    - **Property 8: Body Format Structure**
    - Generate random CmcdV2Data serialized in body mode
    - Verify: output consists of LF-separated CMCD records in query-argument format (comma-separated key=value without URL encoding and without header group prefixes)
    - Verify: records with no populated keys are omitted
    - Verify: single record has no trailing LF
    - Verify: multiple records separated by LF characters (0x0A)
    - **Validates: Requirements 4.1, 4.3, 4.4, 4.5, 15.4**

  - [x]* 7.6 Write unit tests for `CmcdEventReporter`
    - Test event dispatch to multiple targets
    - Test heartbeat scheduling at 30-second default interval with mocked time (Handler)
    - Test graceful error handling on HTTP failure
    - Test per-target key and event filtering
    - Test Authorization header inclusion/omission
    - Test stop() cancels pending heartbeats
    - _Requirements: 3.1, 3.3, 3.4, 3.7, 3.9, 11.3, 11.4, 12.2, 12.3_

- [x] 8. Checkpoint - Ensure Event Mode tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Integrate with chunk sources and DefaultMediaSourceFactory
  - [x] 9.1 Update `DefaultMediaSourceFactory` to accept `CmcdConfigurationFactory`
    - Add `setCmcdConfigurationFactory(CmcdConfigurationFactory factory)` method
    - Maintain existing `setCmcdConfigurationFactory(CmcdConfiguration.Factory factory)` for backward compatibility
    - Route version resolution: pass `CmcdConfiguration` to chunk sources for v1, `CmcdV2Configuration` for v2
    - Start `CmcdEventReporter` when v2 configuration with event targets is active
    - Stop `CmcdEventReporter` on media item change or playback end
    - _Requirements: 1.1, 1.4, 1.5, 2.1, 2.4_

  - [x] 9.2 Update DASH `DefaultDashChunkSource` for v2 support
    - Detect v2 configuration and use `CmcdV2Data.Factory` for data assembly
    - Populate sequence number (`sn`) — a monotonically increasing integer tracking the sequence of CMCD reports to a target within a session
    - Set stream type to `ll` when stream is identified as low-latency
    - Preserve existing v1 behavior when v1 configuration is active
    - _Requirements: 14.1, 14.2, 14.3, 14.4_

  - [x] 9.3 Update HLS `HlsChunkSource` for v2 support
    - Detect v2 configuration and use `CmcdV2Data.Factory` for data assembly
    - Populate sequence number (`sn`) — a monotonically increasing integer tracking the sequence of CMCD reports to a target within a session
    - Set stream type to `ll` when low-latency HLS is detected
    - Preserve existing v1 behavior when v1 configuration is active
    - _Requirements: 14.1, 14.2, 14.3, 14.4_

  - [x] 9.4 Update SmoothStreaming `DefaultSsChunkSource` for v2 support
    - Detect v2 configuration and use `CmcdV2Data.Factory` for data assembly
    - Populate sequence number (`sn`) — a monotonically increasing integer tracking the sequence of CMCD reports to a target within a session
    - Preserve existing v1 behavior when v1 configuration is active
    - _Requirements: 14.1, 14.2, 14.4_

  - [x]* 9.5 Write integration tests for chunk source v2 support
    - Test `DefaultDashChunkSource` produces correct v2 payloads with sequence number and low-latency stream type
    - Test `HlsChunkSource` produces correct v2 payloads with sequence number
    - Test `DefaultSsChunkSource` produces correct v2 payloads
    - Test v1 configuration produces unchanged v1 output in all chunk sources
    - Test mixed-mode request payloads use v1 syntax
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 2.4, 10.1_

- [x] 10. Implement backward compatibility verification
  - [x] 10.1 Write integration tests for backward compatibility
    - Verify v1 `CmcdConfiguration.Factory` set on `DefaultMediaSourceFactory` produces identical output to existing implementation
    - Verify no `v` key in v1 payloads
    - Verify existing header and query parameter modes unchanged
    - Verify body mode for Event Mode produces correct LF-separated query-argument format
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 4.1, 4.5_

- [x] 11. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document using jqwik (PBT library for Java/JUnit 5)
- Unit tests validate specific examples and edge cases
- The implementation uses Java, consistent with the existing AndroidX Media3 codebase
- All new classes are annotated with `@UnstableApi` per Media3 conventions
- Existing v1 classes (`CmcdConfiguration`, `CmcdConfiguration.Factory`, `CmcdData`, `CmcdData.Factory`) remain completely unchanged
- Body transmission mode is only valid for Event Mode; batch object data transfer mode is explicitly not allowed with Request Mode per the CTA-5004-B spec
- The `sn` key represents a sequence number (monotonically increasing integer identifying the sequence of CMCD reports to a target within a session, reset to zero on new session-id), not a segment number
- Buffer starvation keys (`bsa`, `bsd`, `bsda`) use InnerListValue type, not scalar long

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "2.1"] },
    { "id": 2, "tasks": ["2.2", "4.1", "4.2"] },
    { "id": 3, "tasks": ["2.3", "2.4", "2.5", "2.6", "2.7", "4.3"] },
    { "id": 4, "tasks": ["4.4", "4.5", "5.1"] },
    { "id": 5, "tasks": ["5.2", "5.3"] },
    { "id": 6, "tasks": ["5.4", "5.5", "5.6", "5.7", "5.8", "5.9", "5.10"] },
    { "id": 7, "tasks": ["7.1"] },
    { "id": 8, "tasks": ["7.2", "7.3", "7.4", "7.5", "7.6"] },
    { "id": 9, "tasks": ["9.1"] },
    { "id": 10, "tasks": ["9.2", "9.3", "9.4"] },
    { "id": 11, "tasks": ["9.5", "10.1"] }
  ]
}
```
