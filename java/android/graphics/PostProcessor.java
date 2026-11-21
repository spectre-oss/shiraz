// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Custom post-decode drawing callback for ImageDecoder and AnimatedImageDrawable. */
public interface PostProcessor {
    int onPostProcess(Canvas canvas);
}
