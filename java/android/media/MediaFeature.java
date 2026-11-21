// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public final class MediaFeature {
    public MediaFeature() {}

    public static final class HdrType {
        public static final String DOLBY_VISION = "android.media.feature.hdr.dolby_vision";
        public static final String HDR10 = "android.media.feature.hdr.hdr10";
        public static final String HDR10_PLUS = "android.media.feature.hdr.hdr10_plus";
        public static final String HLG = "android.media.feature.hdr.hlg";

        private HdrType() {}
    }
}
