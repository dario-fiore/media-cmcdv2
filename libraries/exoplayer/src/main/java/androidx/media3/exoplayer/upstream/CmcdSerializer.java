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

import android.net.Uri;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serializes CMCD data into spec-compliant string representations.
 *
 * <p>Supports header mode, query parameter mode (for Request Mode), and body mode formatting (for
 * Event Mode only) for both v1 and v2 payload syntax.
 *
 * <p>Value formatting rules per CTA-5004 / CTA-5004-B:
 *
 * <ul>
 *   <li>Integer values → bare numbers (e.g., {@code br=5000})
 *   <li>Boolean {@code true} → key only, no equals sign (e.g., {@code bs})
 *   <li>String values → double-quoted (e.g., {@code sid="abc"})
 *   <li>Float values → two decimal places (e.g., {@code rtp=1200.50})
 *   <li>{@link InnerListValue} → RFC 8941 inner list syntax (e.g., {@code br=(5000;v 320;a)})
 * </ul>
 */
@UnstableApi
public final class CmcdSerializer {

  private CmcdSerializer() {}

  /**
   * Serializes CMCD data to header-mode format (4 separate header strings).
   *
   * <p>Returns a map of header group name (e.g., "CMCD-Object") to a formatted comma-separated
   * key-value string. Keys within each header group are sorted alphabetically. Header groups with
   * no populated keys are omitted from the result.
   *
   * @param data The {@link CmcdKeyValueStore} containing the CMCD data to serialize.
   * @return An {@link ImmutableMap} of header names to formatted value strings.
   */
  public static ImmutableMap<String, String> toHeaders(CmcdKeyValueStore data) {
    ImmutableMap.Builder<String, String> headers = ImmutableMap.builder();

    String objectHeader = formatGroup(data.getObjectKeys());
    if (!objectHeader.isEmpty()) {
      headers.put(CmcdConfiguration.KEY_CMCD_OBJECT, objectHeader);
    }

    String requestHeader = formatGroup(data.getRequestKeys());
    if (!requestHeader.isEmpty()) {
      headers.put(CmcdConfiguration.KEY_CMCD_REQUEST, requestHeader);
    }

    String sessionHeader = formatGroup(data.getSessionKeys());
    if (!sessionHeader.isEmpty()) {
      headers.put(CmcdConfiguration.KEY_CMCD_SESSION, sessionHeader);
    }

    String statusHeader = formatGroup(data.getStatusKeys());
    if (!statusHeader.isEmpty()) {
      headers.put(CmcdConfiguration.KEY_CMCD_STATUS, statusHeader);
    }

    return headers.buildOrThrow();
  }

  /**
   * Serializes CMCD data to query-parameter-mode format (single URL-encoded string).
   *
   * <p>Combines all header group keys (Object, Request, Session, Status) into one alphabetically
   * sorted comma-separated string, then URL-encodes the result.
   *
   * @param data The {@link CmcdKeyValueStore} containing the CMCD data to serialize.
   * @return A URL-encoded string suitable for use as the value of the {@code CMCD} query parameter.
   */
  public static String toQueryParameter(CmcdKeyValueStore data) {
    String rawValue = toRawQueryParameter(data);
    if (rawValue.isEmpty()) {
      return "";
    }
    return Uri.encode(rawValue);
  }

  /**
   * Serializes CMCD data to query-parameter-mode format without URL encoding.
   *
   * <p>Combines all header group keys (Object, Request, Session, Status) into one alphabetically
   * sorted comma-separated string. Useful when the caller will handle encoding (e.g., via {@code
   * Uri.Builder.appendQueryParameter}).
   *
   * @param data The {@link CmcdKeyValueStore} containing the CMCD data to serialize.
   * @return A raw comma-separated string of CMCD key-value pairs, or empty if no keys are set.
   */
  public static String toRawQueryParameter(CmcdKeyValueStore data) {
    // Merge all header group keys into a single sorted map
    TreeMap<String, Object> allKeys = new TreeMap<>();
    allKeys.putAll(data.getObjectKeys());
    allKeys.putAll(data.getRequestKeys());
    allKeys.putAll(data.getSessionKeys());
    allKeys.putAll(data.getStatusKeys());

    if (allKeys.isEmpty()) {
      return "";
    }

    List<String> formattedPairs = new ArrayList<>();
    for (Map.Entry<String, Object> entry : allKeys.entrySet()) {
      formattedPairs.add(formatValue(entry.getKey(), entry.getValue()));
    }

    return joinPairs(formattedPairs);
  }

  /**
   * Serializes CMCD data to body-mode format (LF-separated records, no URL encoding, no header
   * group prefixes).
   *
   * <p>Each record is a comma-separated list of key=value pairs in query-argument format. Multiple
   * records are separated by a single Line Feed character (Unicode 0x0A). Records with no populated
   * keys are omitted. If only a single record is present, no trailing LF is included.
   *
   * @param data The {@link CmcdKeyValueStore} containing the CMCD data to serialize.
   * @return A body-mode formatted string.
   */
  public static String toBody(CmcdKeyValueStore data) {
    // For body mode, combine all keys (including event-only) into one record per logical group.
    // Per the CTA-5004-B spec, body mode uses query-argument format without header group prefixes.
    // We merge all keys into a single record (or multiple records if the implementation provides
    // multiple logical records). The standard interpretation is all keys in one record.
    TreeMap<String, Object> allKeys = new TreeMap<>();
    allKeys.putAll(data.getObjectKeys());
    allKeys.putAll(data.getRequestKeys());
    allKeys.putAll(data.getSessionKeys());
    allKeys.putAll(data.getStatusKeys());
    allKeys.putAll(data.getEventOnlyKeys());

    List<String> records = new ArrayList<>();

    if (!allKeys.isEmpty()) {
      List<String> formattedPairs = new ArrayList<>();
      for (Map.Entry<String, Object> entry : allKeys.entrySet()) {
        formattedPairs.add(formatValue(entry.getKey(), entry.getValue()));
      }
      records.add(joinPairs(formattedPairs));
    }

    // Filter out empty records
    List<String> nonEmptyRecords = new ArrayList<>();
    for (String record : records) {
      if (!record.isEmpty()) {
        nonEmptyRecords.add(record);
      }
    }

    if (nonEmptyRecords.isEmpty()) {
      return "";
    }

    // Join with LF (0x0A). No trailing LF for single record.
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < nonEmptyRecords.size(); i++) {
      if (i > 0) {
        sb.append('\n');
      }
      sb.append(nonEmptyRecords.get(i));
    }
    return sb.toString();
  }

  /**
   * Formats a single key-value pair according to CMCD formatting rules.
   *
   * <ul>
   *   <li>Integer ({@link Integer} or {@link Long}) → bare number (e.g., {@code br=5000})
   *   <li>Boolean {@code true} → key only (e.g., {@code bs})
   *   <li>String → double-quoted (e.g., {@code sid="abc"})
   *   <li>Float ({@link Float} or {@link Double}) → 2 decimal places (e.g., {@code pr=1.50})
   *   <li>{@link InnerListValue} → RFC 8941 inner list syntax (e.g., {@code br=(5000;v 320;a)})
   * </ul>
   *
   * @param key The CMCD key name.
   * @param value The value to format.
   * @return The formatted key=value string.
   */
  static String formatValue(String key, Object value) {
    if (value instanceof Boolean) {
      if ((Boolean) value) {
        // Boolean true is represented as key-only (no =)
        return key;
      } else {
        // Boolean false: per CMCD spec, boolean false should not be transmitted.
        // However, if explicitly provided, we omit the value (key only is true).
        // False values should not normally appear; return empty to be safe.
        return key + "=false";
      }
    } else if (value instanceof Integer) {
      return key + "=" + value;
    } else if (value instanceof Long) {
      return key + "=" + value;
    } else if (value instanceof Float) {
      return String.format(Locale.US, "%s=%.2f", key, (float) value);
    } else if (value instanceof Double) {
      return String.format(Locale.US, "%s=%.2f", key, (double) value);
    } else if (value instanceof String) {
      return String.format(Locale.US, "%s=\"%s\"", key, value);
    } else if (value instanceof InnerListValue) {
      return key + "=" + formatInnerList((InnerListValue) value);
    } else {
      // Fallback: treat as token (unquoted string, e.g., for stream type tokens like "v", "l")
      return key + "=" + value;
    }
  }

  /**
   * Formats an {@link InnerListValue} to RFC 8941 inner list syntax.
   *
   * <p>Inner lists are formatted as: {@code (item1;token1 item2;token2)} where items are
   * space-separated within parentheses. Each item's value is followed by a semicolon and its type
   * token (if present). String values within the inner list are double-quoted. Items with a range
   * parameter use the format {@code "url";r=range}.
   *
   * @param innerList The {@link InnerListValue} to format.
   * @return The RFC 8941 inner list string representation.
   */
  static String formatInnerList(InnerListValue innerList) {
    StringBuilder sb = new StringBuilder();
    sb.append('(');

    for (int i = 0; i < innerList.items.size(); i++) {
      if (i > 0) {
        sb.append(' ');
      }
      InnerListValue.Item item = innerList.items.get(i);

      // Format the item value
      if (item.value instanceof String) {
        sb.append('"').append(item.value).append('"');
      } else {
        sb.append(item.value);
      }

      // Append type token if present
      if (item.typeToken != null) {
        sb.append(';').append(item.typeToken);
      }

      // Append range parameter if present
      if (item.rangeParam != null) {
        sb.append(";r=").append(item.rangeParam);
      }
    }

    sb.append(')');
    return sb.toString();
  }

  /**
   * Formats a group of key-value pairs into a comma-separated string with keys sorted
   * alphabetically.
   */
  private static String formatGroup(ImmutableMap<String, Object> keys) {
    if (keys.isEmpty()) {
      return "";
    }

    // Sort keys alphabetically
    List<String> sortedKeys = new ArrayList<>(keys.keySet());
    Collections.sort(sortedKeys);

    List<String> formattedPairs = new ArrayList<>();
    for (String key : sortedKeys) {
      formattedPairs.add(formatValue(key, keys.get(key)));
    }

    return joinPairs(formattedPairs);
  }

  /** Joins a list of formatted key=value pairs with commas. */
  private static String joinPairs(List<String> pairs) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < pairs.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(pairs.get(i));
    }
    return sb.toString();
  }
}
