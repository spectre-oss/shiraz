// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public interface AudioMetadataReadMap {
    <T> boolean containsKey(AudioMetadata.Key<T> key);

    AudioMetadataMap dup();

    <T> T get(AudioMetadata.Key<T> key);

    int size();
}
