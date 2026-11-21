// SPDX-License-Identifier: GPL-3.0-only
package android.media;

public class CameraProfile {
    public static final int QUALITY_LOW = 0;
    public static final int QUALITY_MEDIUM = 1;
    public static final int QUALITY_HIGH = 2;

    public CameraProfile() {}

    public static int getJpegEncodingQualityParameter(int quality) {
        return getJpegEncodingQualityParameter(0, quality);
    }

    public static int getJpegEncodingQualityParameter(int cameraId, int quality) {
        switch (quality) {
            case QUALITY_LOW:
                return 70;
            case QUALITY_MEDIUM:
                return 80;
            case QUALITY_HIGH:
                return 95;
            default:
                throw new IllegalArgumentException("Unsupported quality level: " + quality);
        }
    }
}
