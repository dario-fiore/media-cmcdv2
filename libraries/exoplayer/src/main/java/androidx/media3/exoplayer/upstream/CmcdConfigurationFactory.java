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

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;

/**
 * Factory for creating version-specific CMCD configurations per MediaItem.
 *
 * <p>Returns either a {@link CmcdConfiguration} for v1 behavior or a {@link CmcdV2Configuration}
 * for v2/mixed behavior. Returning {@code null} disables CMCD for that MediaItem.
 */
@UnstableApi
public interface CmcdConfigurationFactory {

  /**
   * Creates a CMCD configuration for the given media item.
   *
   * @param mediaItem The media item to configure CMCD for.
   * @return A {@link CmcdConfiguration} (v1), {@link CmcdV2Configuration} (v2), or {@code null} to
   *     disable CMCD.
   */
  @Nullable
  CmcdConfiguration createCmcdConfiguration(MediaItem mediaItem);
}
