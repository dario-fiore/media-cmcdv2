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
import android.util.Log;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses CMCD-formatted strings back into structured key-value data.
 *
 * <p>Supports header mode, query parameter mode, and body mode parsing. Used for round-trip
 * verification in tests and for consuming CMCD data from external sources.
 *
 * <p>Parsing rules (inverse of {@link CmcdSerializer} formatting):
 *
 * <ul>
 *   <li>Key only (no {@code =}) → Boolean {@code true}
 *   <li>Bare integer → {@link Integer} or {@link Long}
 *   <li>Double-quoted value → {@link String} (quotes removed)
 *   <li>Decimal number → {@link Float} or {@link Double}
 *   <li>Value starting with {@code (} → {@link InnerListValue} (parsed with {@link
 *       #parseInnerList})
 *   <li>Other values → {@link String} (preserved as opaque token)
 * </ul>
 */
@UnstableApi
public final class CmcdParser {

  private static final String TAG = "CmcdParser";

  private CmcdParser() {}

  /**
   * Parses header-mode CMCD strings into a key-value map.
   *
   * <p>Each header group value is a comma-separated list of key=value pairs. All header groups are
   * merged into a single map.
   *
   * @param headers An {@link ImmutableMap} of header group names (e.g., "CMCD-Object") to their
   *     formatted value strings.
   * @return An {@link ImmutableMap} of parsed key-value pairs from all header groups combined.
   */
  public static ImmutableMap<String, Object> fromHeaders(ImmutableMap<String, String> headers) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      String headerValue = entry.getValue();
      if (headerValue != null && !headerValue.isEmpty()) {
        parseCommaSeparatedPairs(headerValue, result);
      }
    }
    return ImmutableMap.copyOf(result);
  }

  /**
   * Parses a query-parameter-mode CMCD string into a key-value map.
   *
   * <p>The input is URL-decoded first, then parsed as comma-separated key=value pairs.
   *
   * @param queryValue The URL-encoded query parameter value.
   * @return An {@link ImmutableMap} of parsed key-value pairs.
   */
  public static ImmutableMap<String, Object> fromQueryParameter(String queryValue) {
    if (queryValue == null || queryValue.isEmpty()) {
      return ImmutableMap.of();
    }
    String decoded = Uri.decode(queryValue);
    Map<String, Object> result = new LinkedHashMap<>();
    parseCommaSeparatedPairs(decoded, result);
    return ImmutableMap.copyOf(result);
  }

  /**
   * Parses a body-mode CMCD string into a key-value map.
   *
   * <p>The body consists of one or more CMCD records separated by Line Feed characters (Unicode
   * 0x0A). Each record is parsed as comma-separated key=value pairs. All records are merged into a
   * single map.
   *
   * @param body The body-mode formatted string.
   * @return An {@link ImmutableMap} of parsed key-value pairs from all records combined.
   */
  public static ImmutableMap<String, Object> fromBody(String body) {
    if (body == null || body.isEmpty()) {
      return ImmutableMap.of();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    String[] records = body.split("\n", -1);
    for (String record : records) {
      if (!record.isEmpty()) {
        parseCommaSeparatedPairs(record, result);
      }
    }
    return ImmutableMap.copyOf(result);
  }

  /**
   * Parses an RFC 8941 inner list string into an {@link InnerListValue}.
   *
   * <p>The input should be in the format {@code (item1;token1 item2;token2)} where items are
   * space-separated within parentheses. String values are double-quoted. Items may have a type
   * token (e.g., {@code ;v}, {@code ;a}, {@code ;av}) or a range parameter (e.g., {@code
   * ;r=range}).
   *
   * @param innerListStr The inner list string to parse (including outer parentheses).
   * @return The reconstructed {@link InnerListValue}.
   */
  static InnerListValue parseInnerList(String innerListStr) {
    InnerListValue.Builder builder = new InnerListValue.Builder();

    // Strip outer parentheses
    String content = innerListStr.trim();
    if (content.startsWith("(") && content.endsWith(")")) {
      content = content.substring(1, content.length() - 1);
    }

    if (content.isEmpty()) {
      return builder.build();
    }

    // Split items by spaces, but respect quoted strings
    int i = 0;
    while (i < content.length()) {
      // Skip leading whitespace
      while (i < content.length() && content.charAt(i) == ' ') {
        i++;
      }
      if (i >= content.length()) {
        break;
      }

      String itemValue;
      String typeToken = null;
      String rangeParam = null;

      if (content.charAt(i) == '"') {
        // Quoted string value
        int closeQuote = content.indexOf('"', i + 1);
        if (closeQuote == -1) {
          // Malformed: no closing quote, take rest as value
          Log.w(TAG, "Malformed inner list item: missing closing quote");
          itemValue = content.substring(i + 1);
          i = content.length();
        } else {
          itemValue = content.substring(i + 1, closeQuote);
          i = closeQuote + 1;
        }

        // Parse parameters after the quoted string (;token or ;r=range)
        while (i < content.length() && content.charAt(i) == ';') {
          i++; // skip ';'
          int paramEnd = i;
          while (paramEnd < content.length() && content.charAt(paramEnd) != ' '
              && content.charAt(paramEnd) != ';') {
            paramEnd++;
          }
          String param = content.substring(i, paramEnd);
          i = paramEnd;

          if (param.startsWith("r=")) {
            rangeParam = param.substring(2);
          } else {
            typeToken = param;
          }
        }

        if (rangeParam != null) {
          builder.addItemWithRange(itemValue, rangeParam);
        } else if (typeToken != null) {
          builder.addItem(itemValue, typeToken);
        } else {
          builder.addItemWithRange(itemValue, null);
        }
      } else {
        // Numeric or unquoted token value
        int valueEnd = i;
        while (valueEnd < content.length() && content.charAt(valueEnd) != ' '
            && content.charAt(valueEnd) != ';') {
          valueEnd++;
        }
        String rawValue = content.substring(i, valueEnd);
        i = valueEnd;

        // Parse parameters
        while (i < content.length() && content.charAt(i) == ';') {
          i++; // skip ';'
          int paramEnd = i;
          while (paramEnd < content.length() && content.charAt(paramEnd) != ' '
              && content.charAt(paramEnd) != ';') {
            paramEnd++;
          }
          String param = content.substring(i, paramEnd);
          i = paramEnd;

          if (param.startsWith("r=")) {
            rangeParam = param.substring(2);
          } else {
            typeToken = param;
          }
        }

        // Parse numeric value
        Object parsedValue = parseNumericValue(rawValue);
        if (parsedValue instanceof String && rangeParam != null) {
          builder.addItemWithRange((String) parsedValue, rangeParam);
        } else {
          builder.addItem(parsedValue, typeToken);
        }
      }
    }

    return builder.build();
  }

  /**
   * Parses a comma-separated string of key=value pairs into the given result map. Handles inner
   * list values (parenthesized) that may contain commas within items.
   */
  private static void parseCommaSeparatedPairs(String input, Map<String, Object> result) {
    // Split by commas, but respect parenthesized inner lists and quoted strings
    int i = 0;
    while (i < input.length()) {
      // Skip leading whitespace
      while (i < input.length() && input.charAt(i) == ' ') {
        i++;
      }
      if (i >= input.length()) {
        break;
      }

      // Find the end of this key=value pair
      int pairEnd = findPairEnd(input, i);
      String pair = input.substring(i, pairEnd).trim();
      i = pairEnd + 1; // skip the comma

      if (pair.isEmpty()) {
        continue;
      }

      try {
        parseSinglePair(pair, result);
      } catch (Exception e) {
        Log.w(TAG, "Failed to parse CMCD pair: " + pair, e);
      }
    }
  }

  /**
   * Finds the end index of a key=value pair, respecting parenthesized inner lists and quoted
   * strings.
   */
  private static int findPairEnd(String input, int start) {
    int depth = 0;
    boolean inQuotes = false;
    for (int i = start; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '"' && depth == 0) {
        inQuotes = !inQuotes;
      } else if (!inQuotes) {
        if (c == '(') {
          depth++;
        } else if (c == ')') {
          depth--;
        } else if (c == ',' && depth == 0) {
          return i;
        }
      }
    }
    return input.length();
  }

  /** Parses a single key=value pair and adds it to the result map. */
  private static void parseSinglePair(String pair, Map<String, Object> result) {
    // Find the '=' separator. Need to be careful not to match '=' inside
    // parenthesized inner lists or quoted strings.
    int equalsIndex = findEqualsIndex(pair);

    if (equalsIndex == -1) {
      // Key only → Boolean true
      String key = pair.trim();
      if (!key.isEmpty()) {
        result.put(key, true);
      }
      return;
    }

    String key = pair.substring(0, equalsIndex).trim();
    String rawValue = pair.substring(equalsIndex + 1);

    if (key.isEmpty()) {
      return;
    }

    Object parsedValue = parseValue(rawValue);
    result.put(key, parsedValue);
  }

  /** Finds the index of the first top-level '=' in a pair string. */
  private static int findEqualsIndex(String pair) {
    int depth = 0;
    boolean inQuotes = false;
    for (int i = 0; i < pair.length(); i++) {
      char c = pair.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (!inQuotes) {
        if (c == '(') {
          depth++;
        } else if (c == ')') {
          depth--;
        } else if (c == '=' && depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  /** Parses a raw value string into the appropriate typed object. */
  private static Object parseValue(String rawValue) {
    if (rawValue.isEmpty()) {
      return "";
    }

    // Inner list: starts with '('
    if (rawValue.startsWith("(")) {
      return parseInnerList(rawValue);
    }

    // Quoted string: starts and ends with '"'
    if (rawValue.startsWith("\"") && rawValue.endsWith("\"") && rawValue.length() >= 2) {
      return rawValue.substring(1, rawValue.length() - 1);
    }

    // Boolean false (rare, but handle it)
    if (rawValue.equals("false")) {
      return false;
    }

    // Try numeric parsing
    return parseNumericValue(rawValue);
  }

  /**
   * Attempts to parse a value as a number. Returns Integer for small integers, Long for large
   * integers, Float/Double for decimal numbers, or the original String if not numeric.
   */
  private static Object parseNumericValue(String value) {
    if (value.isEmpty()) {
      return value;
    }

    // Check if it contains a decimal point → floating point
    if (value.contains(".")) {
      try {
        double d = Double.parseDouble(value);
        // Use Float if value fits within float precision (matches CmcdSerializer 2 decimal places)
        float f = Float.parseFloat(value);
        if (String.format(java.util.Locale.US, "%.2f", f).equals(
            String.format(java.util.Locale.US, "%.2f", d))) {
          return f;
        }
        return d;
      } catch (NumberFormatException e) {
        return value;
      }
    }

    // Try integer parsing
    try {
      long l = Long.parseLong(value);
      if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
        return (int) l;
      }
      return l;
    } catch (NumberFormatException e) {
      // Not a number, treat as opaque token string
      return value;
    }
  }
}
