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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an RFC 8941 inner list value for CMCD v2 per-object-type keys.
 *
 * <p>An inner list pairs values with optional type tokens that identify the object type (video,
 * audio, or muxed audio and video). This enables a single CMCD key to carry values for multiple
 * media types simultaneously.
 *
 * <p>Example: {@code br=(5000;v 320;a)} represents bitrate 5000 for video, 320 for audio.
 */
@UnstableApi
public final class InnerListValue {

  /** A single item within an inner list, pairing a value with a type token. */
  public static final class Item {

    /** The numeric or string value. */
    public final Object value;

    /**
     * The object type token (e.g., "v", "a", "av"), or {@code null} if no type token is
     * associated with this item.
     */
    @Nullable public final String typeToken;

    /** Range parameter for 'nor' entries, or {@code null} if not applicable. */
    @Nullable public final String rangeParam;

    private Item(Object value, @Nullable String typeToken, @Nullable String rangeParam) {
      this.value = value;
      this.typeToken = typeToken;
      this.rangeParam = rangeParam;
    }
  }

  /** The ordered list of items. */
  public final ImmutableList<Item> items;

  private InnerListValue(ImmutableList<Item> items) {
    this.items = items;
  }

  /** Builder for constructing {@link InnerListValue} instances. */
  public static final class Builder {

    private static final String TYPE_TOKEN_VIDEO = "v";
    private static final String TYPE_TOKEN_AUDIO = "a";
    private static final String TYPE_TOKEN_MUXED = "av";

    private final List<Item> items;

    /** Creates a new instance. */
    public Builder() {
      this.items = new ArrayList<>();
    }

    /**
     * Adds an item with the given value and type token.
     *
     * @param value The numeric or string value for this item.
     * @param typeToken The object type token ("v", "a", "av"), or {@code null} for items without a
     *     type token.
     * @return This builder, for convenience.
     * @throws IllegalArgumentException If {@code typeToken} is not one of "v", "a", "av", or
     *     {@code null}.
     */
    @CanIgnoreReturnValue
    public Builder addItem(Object value, @Nullable String typeToken) {
      checkNotNull(value);
      validateTypeToken(typeToken);
      items.add(new Item(value, typeToken, /* rangeParam= */ null));
      return this;
    }

    /**
     * Adds an item with a string value and an optional range parameter, typically used for 'nor'
     * entries.
     *
     * @param value The string value (e.g., a URL) for this item.
     * @param range The range parameter associated with this item, or {@code null} if no range is
     *     specified.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder addItemWithRange(String value, @Nullable String range) {
      checkNotNull(value);
      items.add(new Item(value, /* typeToken= */ null, range));
      return this;
    }

    /**
     * Builds the {@link InnerListValue}.
     *
     * @return A new immutable {@link InnerListValue} containing the items added to this builder.
     */
    public InnerListValue build() {
      return new InnerListValue(ImmutableList.copyOf(items));
    }

    private static void validateTypeToken(@Nullable String typeToken) {
      if (typeToken != null) {
        checkArgument(
            typeToken.equals(TYPE_TOKEN_VIDEO)
                || typeToken.equals(TYPE_TOKEN_AUDIO)
                || typeToken.equals(TYPE_TOKEN_MUXED),
            "Invalid type token: %s. Must be one of \"v\", \"a\", \"av\", or null.",
            typeToken);
      }
    }
  }
}
