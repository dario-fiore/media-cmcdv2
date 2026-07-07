# Requirements Document

## Introduction

This document defines the requirements for implementing CMCD v2 (CTA-5004-B) support in AndroidX Media3 / ExoPlayer. The implementation adds the new v2 capabilities—including Event Mode reporting, body transmission, inner list syntax, and expanded reserved keys—while maintaining full backward compatibility with the existing v1 (CTA-5004) implementation. A version-aware architecture with separate factories enables clean coexistence of both protocol versions.

## Glossary

- **CMCD_System**: The set of classes responsible for assembling and transmitting Common Media Client Data (CMCD) in AndroidX Media3, including configuration, data assembly, and HTTP attachment components.
- **Event_Mode**: A CTA-5004-B reporting mechanism where the player sends CMCD data to alternate destinations via HTTP POST requests, triggered by events or heartbeat intervals, independent of media object requests.
- **Request_Mode**: The original CTA-5004 reporting mechanism where CMCD data is attached to media object HTTP requests as headers or query parameters.
- **Body_Transmission**: A CTA-5004-B data transmission method that sends CMCD records as newline-separated key-value pairs in an HTTP POST body with content-type "application/cmcd". Body transmission is only valid for Event Mode.
- **Inner_List_Syntax**: A CTA-5004-B value format where keys support per-object-type values using structured field inner list notation with token identifiers, e.g., `br=(5000;v 320;a)`.
- **CMCD_Configuration_Factory**: The factory interface that creates version-specific CMCD configuration instances per MediaItem.
- **Event_Target**: A destination endpoint configured to receive Event Mode CMCD reports via HTTP POST.
- **Heartbeat_Interval**: A configurable time period between periodic Event Mode reports sent to an Event Target.
- **Chunk_Source**: A streaming-format-specific component (DASH, HLS, SmoothStreaming) that produces media chunks and attaches CMCD data to their HTTP requests.
- **DataSpec**: An internal representation of an HTTP request specification to which CMCD data is attached before network dispatch.

## Requirements

### Requirement 1: Version-Aware Configuration Factory

**User Story:** As a developer, I want a version-aware configuration factory architecture, so that I can create v1-only, v2-only, or mixed-version CMCD configurations per MediaItem without breaking existing integrations.

#### Acceptance Criteria

1. THE CMCD_System SHALL provide a v2-capable configuration factory interface that creates version-specific configurations per MediaItem.
2. WHEN a developer uses the existing v1 CmcdConfiguration.Factory, THE CMCD_System SHALL continue to produce v1-only CMCD behavior with no changes to output or semantics.
3. WHEN a v2 configuration is created, THE CMCD_System SHALL include the version key `v=2` in every CMCD payload.
4. THE CMCD_System SHALL allow a single player instance to use different CMCD versions for different MediaItems by returning version-specific configurations from the factory.
5. WHEN a v2 configuration factory returns null for a MediaItem, THE CMCD_System SHALL disable CMCD for that MediaItem.

---

### Requirement 2: Backward Compatibility

**User Story:** As a developer with an existing CMCD v1 integration, I want the v2 implementation to preserve all existing v1 APIs and behavior, so that I do not need to modify my application when upgrading the library.

#### Acceptance Criteria

1. THE CMCD_System SHALL preserve the existing CmcdConfiguration class, CmcdConfiguration.Factory interface, CmcdData class, and CmcdData.Factory class without removing or renaming public API members.
2. THE CMCD_System SHALL maintain the existing behavior where the `v` key is omitted from payloads when operating in v1 mode.
3. THE CMCD_System SHALL continue to support MODE_REQUEST_HEADER and MODE_QUERY_PARAMETER transmission modes for v1 configurations.
4. WHEN a v1 CmcdConfiguration.Factory is set on DefaultMediaSourceFactory, THE CMCD_System SHALL produce output identical to the current v1 implementation.
5. THE CMCD_System SHALL maintain the existing content ID maximum length of 64 characters for v1 configurations.

---

### Requirement 3: Event Mode Reporting

**User Story:** As a developer, I want to configure Event Mode so that CMCD data can be reported to analytics endpoints independently of media object requests.

#### Acceptance Criteria

1. WHEN Event Mode is enabled in the v2 configuration, THE CMCD_System SHALL send CMCD data to configured Event Targets via HTTP POST requests.
2. THE CMCD_System SHALL support configuring one or more Event Targets with independent endpoint URLs.
3. WHEN a Heartbeat Interval is configured for an Event Target, THE CMCD_System SHALL send periodic CMCD reports at the specified interval while playback is active.
4. WHEN a triggering event occurs, THE CMCD_System SHALL send an event-triggered CMCD report to all configured Event Targets that accept that event type, using the CTA-5004-B event type tokens: `abs` (ad break start), `abe` (ad break end), `ae` (app error), `as` (app start), `b` (buffering), `bc` (buffer cleared), `c` (click), `ce` (custom event), `e` (end), `h` (heartbeat), `m` (mute), `pc` (play clear), `pe` (play encrypted), `pr` (play ready), `ps` (pause), `rr` (request rejected), `sk` (seek), `t` (transcode), `um` (unmute).
5. THE CMCD_System SHALL include the event type key `e` in Event Mode reports to identify the triggering event.
6. THE CMCD_System SHALL include the timestamp key `ts` in Event Mode reports to record when the event occurred.
7. IF an HTTP POST to an Event Target fails, THEN THE CMCD_System SHALL handle the error gracefully without interrupting media playback.
8. THE CMCD_System SHALL support per-target key filtering so that each Event Target can receive a different subset of CMCD keys.
9. WHEN Event Mode is enabled, THE CMCD_System SHALL support a configurable heartbeat interval with a default of 30 seconds per the CTA-5004-B specification.

---

### Requirement 4: Body Transmission Mode

**User Story:** As a developer, I want to send CMCD data in an HTTP POST body, so that I can use the v2 body transmission format for Event Mode.

#### Acceptance Criteria

1. WHEN body transmission mode is used for Event Mode, THE CMCD_System SHALL format CMCD data as key-value pairs using the same format as query arguments but without URL encoding.
2. WHEN body transmission mode is used, THE CMCD_System SHALL set the HTTP Content-Type header to "application/cmcd" on the POST request.
3. THE CMCD_System SHALL construct a body object by concatenating one or more CMCD records separated by a single Line Feed (LF) character (Unicode 0x0A).
4. THE CMCD_System SHALL omit empty records from the body output.
5. IF the body contains only a single record, THEN THE CMCD_System SHALL NOT include a trailing Line Feed character.

---

### Requirement 5: Inner List Syntax for Per-Object-Type Values

**User Story:** As a developer, I want the v2 implementation to support inner list syntax for keys that report per-object-type values, so that a single key can carry values for multiple media types simultaneously.

#### Acceptance Criteria

1. WHEN operating in v2 mode, THE CMCD_System SHALL support inner list syntax for the keys `bl`, `br`, `mtp`, `nor`, `tb`, `lab`, `lb`, `tab`, `tbl`, `tpb`, `bsa`, `bsd`, `bsda`, `pb`, `ec`, and `ab`.
2. WHEN inner list syntax is used, THE CMCD_System SHALL format values as structured field inner lists with token identifiers, e.g., `br=(5000;v 320;a)`.
3. WHEN only a single object type value is available, THE CMCD_System SHALL still use inner list notation, as the list syntax MUST always be used even if only a single value is present per CTA-5004-B.
4. THE CMCD_System SHALL use the token identifiers `v` (video), `a` (audio), `av` (muxed audio and video) as per-object-type qualifiers within inner lists.

---

### Requirement 6: Updated 'nor' Key and 'nrr' Key Removal

**User Story:** As a developer, I want the `nor` key to support multiple next object requests and range parameters inline, so that the implementation aligns with the CTA-5004-B specification changes.

#### Acceptance Criteria

1. WHEN operating in v2 mode, THE CMCD_System SHALL format the `nor` key as an inner list of strings supporting multiple next object request URLs.
2. WHEN range information is associated with a next object request in v2 mode, THE CMCD_System SHALL express the range as a parameter within the `nor` inner list entry rather than using a separate `nrr` key.
3. WHEN operating in v1 mode, THE CMCD_System SHALL continue to emit `nor` as a single quoted string and `nrr` as a separate key.
4. THE CMCD_System SHALL omit the `nrr` key entirely from v2 payloads.

---

### Requirement 7: New Reserved Keys

**User Story:** As a developer, I want access to the new CTA-5004-B reserved keys, so that I can report extended quality metrics, buffer analytics, response tracing data, and session management information.

#### Acceptance Criteria

1. THE CMCD_System SHALL define constants for the following new event reporting keys: `e` (event type, CMCD-Request), `sta` (state — a token describing the current playback state of the player: s, p, k, r, a, w, e, f, q, d; CMCD-Request), `ts` (timestamp, CMCD-Request).
2. THE CMCD_System SHALL define constants for the following new quality metric keys: `dfa` (dropped frames accumulated, CMCD-Request), `ltc` (live latency, CMCD-Status), `msd` (media start delay — the initial delay from when a player is instructed to play to when media begins playback, CMCD-Session), `pb` (playhead bitrate — the encoded bitrate of the media object(s) being shown to the end user, inner list type, CMCD-Request), `pt` (playhead time — the playhead time in milliseconds being rendered to the viewer, CMCD-Status).
3. THE CMCD_System SHALL define constants for the following new buffer analytics keys: `bsa` (buffer starvation count accumulated), `bsd` (buffer starvation duration), `bsda` (buffer starvation duration accumulated).
4. THE CMCD_System SHALL define constants for the following new response tracing keys: `cmsdd` (CMSD Dynamic Header — holds a Base64 encoded copy of the CMSD data received on the CMSD-Dynamic response header, String type, Event only), `cmsds` (CMSD Static Header — holds a Base64 encoded copy of the CMSD data received on the CMSD-Static response header, String type, Event only), `rc` (response code), `smrt` (SMRT-Data Header — holds a Base64 encoded copy of the streaming media response tracing data received on the Request Tracing header, String type, Event only), `ttfb` (time to first byte), `ttfbb` (time to first body byte), `ttlb` (time to last byte), `url` (requested URL).
5. THE CMCD_System SHALL define constants for the following new session/management keys: `cen` (custom event name — used to define a custom event name with a maximum of 64 characters, MUST be sent when event type is 'ce', Event only), `h` (hostname — a string identifying the current hostname from which the player is retrieving content with a maximum of 128 characters, Event only), `nr` (non rendered — a boolean that is TRUE when content being retrieved is not rendered as audio/video, CMCD-Status), `sn` (sequence number — a monotonically increasing integer identifying the sequence of a CMCD report to a target within a session, reset to zero on new session-id, CMCD-Request).
6. THE CMCD_System SHALL define a constant for the content integrity key: `cs` (content signature, CMCD-Request).
7. THE CMCD_System SHALL define constants for the following new bitrate-related keys: `lab` (lowest aggregated encoded bitrate, inner list of integer kbps with token identifiers, CMCD-Object), `lb` (lowest encoded bitrate, inner list of integer kbps with token identifiers, CMCD-Object), `tab` (top aggregated encoded bitrate, inner list of integer kbps with token identifiers, CMCD-Object), `tbl` (target buffer length, inner list of integer milliseconds with token identifiers, CMCD-Request), `tpb` (top playable bitrate, inner list of integer kbps with token identifiers, CMCD-Object), `ab` (aggregate encoded bitrate, inner list of integer kbps, CMCD-Object).
8. THE CMCD_System SHALL define constants for the following new status keys: `bg` (backgrounded — a boolean indicating whether the player is backgrounded, CMCD-Status), `ec` (player error code — inner list of strings, CMCD-Status).
9. THE CMCD_System SHALL assign each new key to its appropriate CMCD header group for header-mode transmission.
10. WHEN a new reserved key is populated in the v2 data factory, THE CMCD_System SHALL include the key in the CMCD output according to its assigned header group.

---

### Requirement 8: Extended Content ID Length

**User Story:** As a developer, I want the content ID maximum length increased to 128 characters in v2, so that I can use longer content identifiers as permitted by CTA-5004-B.

#### Acceptance Criteria

1. WHEN a v2 configuration is created with a content ID, THE CMCD_System SHALL allow content IDs up to 128 characters in length.
2. IF a content ID exceeding 128 characters is provided to a v2 configuration, THEN THE CMCD_System SHALL throw an IllegalArgumentException.
3. WHEN a v1 configuration is created, THE CMCD_System SHALL continue to enforce the 64-character maximum for content IDs.

---

### Requirement 9: New Stream Type and Streaming Format Tokens

**User Story:** As a developer, I want the new `ll` (low latency LIVE) stream type and `e` (HESP) streaming format, so that I can accurately report these content types in CMCD payloads.

#### Acceptance Criteria

1. THE CMCD_System SHALL define a new stream type constant `ll` representing low-latency LIVE streams.
2. THE CMCD_System SHALL define a new streaming format constant `e` representing HESP streams.
3. WHEN a stream is identified as low-latency LIVE, THE CMCD_System SHALL emit `st=ll` in the CMCD payload.
4. WHEN a stream uses HESP format, THE CMCD_System SHALL emit `sf=e` in the CMCD payload.

---

### Requirement 10: Mixed Version Support

**User Story:** As a developer, I want to use v1 syntax for Request Mode while simultaneously using v2 syntax for Event Mode, so that I can enable Event Mode reporting without disrupting CDN compatibility of request-attached CMCD data.

#### Acceptance Criteria

1. THE CMCD_System SHALL support a mixed-version configuration where Request Mode uses v1 payload syntax and Event Mode uses v2 payload syntax.
2. WHEN mixed-version mode is active, THE CMCD_System SHALL omit the `v` key from Request Mode payloads (v1 behavior) while including `v=2` in Event Mode payloads.
3. WHEN mixed-version mode is active, THE CMCD_System SHALL use the `nrr` key and single-string `nor` format in Request Mode payloads (v1 behavior).
4. WHEN mixed-version mode is active, THE CMCD_System SHALL use inner list `nor` syntax and omit `nrr` in Event Mode payloads (v2 behavior).

---

### Requirement 11: Per-Target Key and Event Filtering

**User Story:** As a developer, I want to configure which keys and events are sent to each Event Target, so that I can control the data each analytics endpoint receives.

#### Acceptance Criteria

1. THE CMCD_System SHALL support configuring an allowed-key list per Event Target.
2. THE CMCD_System SHALL support configuring an allowed-event-type list per Event Target.
3. WHEN a key is not in an Event Target's allowed-key list, THE CMCD_System SHALL omit that key from reports sent to that target.
4. WHEN an event type is not in an Event Target's allowed-event-type list, THE CMCD_System SHALL suppress the entire report for that event to that target.
5. WHEN no key filter is configured for an Event Target, THE CMCD_System SHALL send all populated keys to that target.

---

### Requirement 12: Access Token Configuration for Event Targets

**User Story:** As a developer, I want to configure access tokens for Event Targets, so that CMCD Event Mode reports can authenticate with secured analytics endpoints.

#### Acceptance Criteria

1. THE CMCD_System SHALL support configuring an access token per Event Target.
2. WHEN an access token is configured for an Event Target, THE CMCD_System SHALL include the token in the HTTP Authorization header of POST requests to that target.
3. WHEN no access token is configured for an Event Target, THE CMCD_System SHALL send POST requests without an Authorization header.

---

### Requirement 13: V2 Data Factory and Key Population

**User Story:** As a developer, I want a v2-aware data factory that can populate both existing and new CMCD keys, so that chunk sources can assemble complete v2 CMCD payloads.

#### Acceptance Criteria

1. THE CMCD_System SHALL provide a v2 data factory that supports setting values for all new CTA-5004-B reserved keys in addition to existing v1 keys.
2. WHEN a v2 data factory is used, THE CMCD_System SHALL automatically populate the `v=2` key in the session group.
3. THE CMCD_System SHALL support setting response tracing keys (`ttfb`, `ttlb`, `rc`, etc.) after the HTTP response is received, for inclusion in subsequent Event Mode reports.
4. THE CMCD_System SHALL support setting accumulated metrics (`dfa`, `bsa`, `bsda`) that persist across requests within a session.
5. WHEN a key value is not set on the v2 data factory, THE CMCD_System SHALL omit that key from the output rather than sending a default or zero value.

---

### Requirement 14: Chunk Source Integration for V2

**User Story:** As a developer, I want the DASH, HLS, and SmoothStreaming chunk sources to support v2 data assembly, so that v2 keys and syntax are correctly populated during media playback.

#### Acceptance Criteria

1. WHEN a v2 configuration is active, THE Chunk_Source SHALL use the v2 data factory to assemble CMCD payloads for media requests.
2. WHEN a v2 configuration is active, THE Chunk_Source SHALL populate sequence number (`sn`) when the information is available from the manifest.
3. WHEN a v2 configuration is active and the stream is identified as low-latency, THE Chunk_Source SHALL set the stream type to `ll`.
4. WHEN a v1 configuration is active, THE Chunk_Source SHALL continue using the existing v1 CmcdData.Factory with no behavioral changes.

---

### Requirement 15: CMCD Payload Serialization and Formatting

**User Story:** As a developer, I want correct serialization of v2 payloads including inner list syntax and body format, so that the output conforms to the CTA-5004-B specification.

#### Acceptance Criteria

1. THE CMCD_System SHALL serialize inner list values using RFC 8941 structured fields inner list syntax: `key=(value1;type1 value2;type2)`.
2. THE CMCD_System SHALL sort keys alphabetically within each header group line, consistent with v1 behavior.
3. THE CMCD_System SHALL format integer values as bare numbers, boolean true values as key-only (no `=`), string values as double-quoted, and float values with two decimal places, consistent with v1 formatting rules.
4. WHEN body transmission mode is used, THE CMCD_System SHALL separate CMCD records with Line Feed characters (Unicode 0x0A).
5. FOR ALL valid CmcdData objects, serializing to a string and parsing back SHALL produce an equivalent set of key-value pairs (round-trip property).

---

### Requirement 16: CMCD Payload Parsing (Round-Trip Support)

**User Story:** As a developer, I want to parse CMCD payloads back into structured data, so that tests can verify serialization correctness and the system can validate round-trip integrity.

#### Acceptance Criteria

1. THE CMCD_System SHALL provide a parser that can reconstruct key-value data from CMCD header-mode formatted strings.
2. THE CMCD_System SHALL provide a parser that can reconstruct key-value data from CMCD body-mode formatted strings.
3. THE CMCD_System SHALL provide a parser that can reconstruct key-value data from CMCD query-parameter-mode formatted strings.
4. WHEN an inner list value is encountered during parsing, THE CMCD_System SHALL reconstruct the per-object-type value map.
5. FOR ALL valid CMCD payloads, parsing then serializing SHALL produce an output equivalent to the original input (round-trip property).
