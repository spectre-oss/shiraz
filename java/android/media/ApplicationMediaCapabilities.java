// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util. LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public final class ApplicationMediaCapabilities implements Parcelable {
    private final Set<String> mSupportedVideoMimeTypes;
    private final Set<String> mUnsupportedVideoMimeTypes;
    private final Set<String> mSupportedHdrTypes;
    private final Set<String> mUnsupportedHdrTypes;

    private ApplicationMediaCapabilities(
            Set<String> supportedVideo,
            Set<String> unsupportedVideo,
            Set<String> supportedHdr,
            Set<String> unsupportedHdr) {
        mSupportedVideoMimeTypes = new LinkedHashSet<String>(supportedVideo);
        mUnsupportedVideoMimeTypes = new LinkedHashSet<String>(unsupportedVideo);
        mSupportedHdrTypes = new LinkedHashSet<String>(supportedHdr);
        mUnsupportedHdrTypes = new LinkedHashSet<String>(unsupportedHdr);
    }

    private ApplicationMediaCapabilities(Parcel in) {
        mSupportedVideoMimeTypes = readStringSet(in);
        mUnsupportedVideoMimeTypes = readStringSet(in);
        mSupportedHdrTypes = readStringSet(in);
        mUnsupportedHdrTypes = readStringSet(in);
    }

    private static Set<String> readStringSet(Parcel in) {
        String[] arr = in.createStringArray();
        Set<String> set = new LinkedHashSet<String>();
        if (arr != null) {
            Collections.addAll(set, arr);
        }
        return set;
    }

    public boolean isVideoMimeTypeSupported(String videoMime) {
        return videoMime != null && mSupportedVideoMimeTypes.contains(videoMime.toLowerCase());
    }

    public boolean isHdrTypeSupported(String hdrType) {
        return hdrType != null && mSupportedHdrTypes.contains(hdrType);
    }

    public boolean isFormatSpecified(String format) {
        if (format == null) return false;
        String lower = format.toLowerCase();
        return mSupportedVideoMimeTypes.contains(lower)
                || mUnsupportedVideoMimeTypes.contains(lower)
                || mSupportedHdrTypes.contains(format)
                || mUnsupportedHdrTypes.contains(format);
    }

    public List<String> getSupportedVideoMimeTypes() {
        return Collections.unmodifiableList(new ArrayList<String>(mSupportedVideoMimeTypes));
    }

    public List<String> getUnsupportedVideoMimeTypes() {
        return Collections.unmodifiableList(new ArrayList<String>(mUnsupportedVideoMimeTypes));
    }

    public List<String> getSupportedHdrTypes() {
        return Collections.unmodifiableList(new ArrayList<String>(mSupportedHdrTypes));
    }

    public List<String> getUnsupportedHdrTypes() {
        return Collections.unmodifiableList(new ArrayList<String>(mUnsupportedHdrTypes));
    }

    public static ApplicationMediaCapabilities createFromXml(XmlPullParser xmlParser) {
        Objects.requireNonNull(xmlParser, "xmlParser");
        Builder builder = new Builder();
        try {
            int eventType = xmlParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "format".equals(xmlParser.getName())) {
                    String name = xmlParser.getAttributeValue(null, "name");
                    String supportedAttr = xmlParser.getAttributeValue(null, "supported");
                    if (name != null && supportedAttr != null) {
                        boolean supported = Boolean.parseBoolean(supportedAttr);
                        applyXmlFormat(builder, name, supported);
                    }
                }
                eventType = xmlParser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            throw new UnsupportedOperationException("Failed to parse media-capabilities XML", e);
        }
        return builder.build();
    }

    private static void applyXmlFormat(Builder builder, String name, boolean supported) {
        String trimmed = name.trim();
        if ("HEVC".equalsIgnoreCase(trimmed) || MediaFormat.MIMETYPE_VIDEO_HEVC.equalsIgnoreCase(trimmed)) {
            if (supported) builder.addSupportedVideoMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC);
            else builder.addUnsupportedVideoMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC);
        } else if ("VP9".equalsIgnoreCase(trimmed) || MediaFormat.MIMETYPE_VIDEO_VP9.equalsIgnoreCase(trimmed)) {
            if (supported) builder.addSupportedVideoMimeType(MediaFormat.MIMETYPE_VIDEO_VP9);
            else builder.addUnsupportedVideoMimeType(MediaFormat.MIMETYPE_VIDEO_VP9);
        } else if ("AV1".equalsIgnoreCase(trimmed) || MediaFormat.MIMETYPE_VIDEO_AV1.equalsIgnoreCase(trimmed)) {
            if (supported) builder.addSupportedVideoMimeType(MediaFormat.MIMETYPE_VIDEO_AV1);
            else builder.addUnsupportedVideoMimeType(MediaFormat.MIMETYPE_VIDEO_AV1);
        } else if ("SlowMotion".equalsIgnoreCase(trimmed)) {
            // Ignored or treated as HDR/feature metadata in AOSP if non-video/hdr
        } else if ("Hdr10".equalsIgnoreCase(trimmed) || MediaFeature.HdrType.HDR10.equals(trimmed)) {
            if (supported) builder.addSupportedHdrType(MediaFeature.HdrType.HDR10);
            else builder.addUnsupportedHdrType(MediaFeature.HdrType.HDR10);
        } else if ("Hdr10Plus".equalsIgnoreCase(trimmed) || MediaFeature.HdrType.HDR10_PLUS.equals(trimmed)) {
            if (supported) builder.addSupportedHdrType(MediaFeature.HdrType.HDR10_PLUS);
            else builder.addUnsupportedHdrType(MediaFeature.HdrType.HDR10_PLUS);
        } else if ("Dolby-Vision".equalsIgnoreCase(trimmed) || "DolbyVision".equalsIgnoreCase(trimmed)
                || MediaFeature.HdrType.DOLBY_VISION.equals(trimmed)) {
            if (supported) builder.addSupportedHdrType(MediaFeature.HdrType.DOLBY_VISION);
            else builder.addUnsupportedHdrType(MediaFeature.HdrType.DOLBY_VISION);
        } else if ("HLG".equalsIgnoreCase(trimmed) || MediaFeature.HdrType.HLG.equals(trimmed)) {
            if (supported) builder.addSupportedHdrType(MediaFeature.HdrType.HLG);
            else builder.addUnsupportedHdrType(MediaFeature.HdrType.HLG);
        } else if (trimmed.startsWith("video/")) {
            if (supported) builder.addSupportedVideoMimeType(trimmed.toLowerCase());
            else builder.addUnsupportedVideoMimeType(trimmed.toLowerCase());
        } else if (trimmed.startsWith("android.media.feature.hdr.")) {
            if (supported) builder.addSupportedHdrType(trimmed);
            else builder.addUnsupportedHdrType(trimmed);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(mSupportedVideoMimeTypes.toArray(new String[0]));
        dest.writeStringArray(mUnsupportedVideoMimeTypes.toArray(new String[0]));
        dest.writeStringArray(mSupportedHdrTypes.toArray(new String[0]));
        dest.writeStringArray(mUnsupportedHdrTypes.toArray(new String[0]));
    }

    @Override
    public String toString() {
        return "ApplicationMediaCapabilities{supportedVideo=" + mSupportedVideoMimeTypes
                + ", unsupportedVideo=" + mUnsupportedVideoMimeTypes
                + ", supportedHdr=" + mSupportedHdrTypes
                + ", unsupportedHdr=" + mUnsupportedHdrTypes + "}";
    }

    public static final Parcelable.Creator<ApplicationMediaCapabilities> CREATOR =
            new Parcelable.Creator<ApplicationMediaCapabilities>() {
                @Override
                public ApplicationMediaCapabilities createFromParcel(Parcel in) {
                    return new ApplicationMediaCapabilities(in);
                }

                @Override
                public ApplicationMediaCapabilities[] newArray(int size) {
                    return new ApplicationMediaCapabilities[size];
                }
            };

    public static final class Builder {
        private final Set<String> mSupportedVideo = new LinkedHashSet<String>();
        private final Set<String> mUnsupportedVideo = new LinkedHashSet<String>();
        private final Set<String> mSupportedHdr = new LinkedHashSet<String>();
        private final Set<String> mUnsupportedHdr = new LinkedHashSet<String>();

        public Builder() {}

        public Builder addSupportedVideoMimeType(String codecMime) {
            Objects.requireNonNull(codecMime, "codecMime");
            String lower = codecMime.toLowerCase();
            mUnsupportedVideo.remove(lower);
            mSupportedVideo.add(lower);
            return this;
        }

        public Builder addUnsupportedVideoMimeType(String codecMime) {
            Objects.requireNonNull(codecMime, "codecMime");
            String lower = codecMime.toLowerCase();
            mSupportedVideo.remove(lower);
            mUnsupportedVideo.add(lower);
            return this;
        }

        public Builder addSupportedHdrType(String hdrType) {
            Objects.requireNonNull(hdrType, "hdrType");
            mUnsupportedHdr.remove(hdrType);
            mSupportedHdr.add(hdrType);
            return this;
        }

        public Builder addUnsupportedHdrType(String hdrType) {
            Objects.requireNonNull(hdrType, "hdrType");
            mSupportedHdr.remove(hdrType);
            mUnsupportedHdr.add(hdrType);
            return this;
        }

        public ApplicationMediaCapabilities build() {
            return new ApplicationMediaCapabilities(
                    mSupportedVideo, mUnsupportedVideo, mSupportedHdr, mUnsupportedHdr);
        }
    }
}
