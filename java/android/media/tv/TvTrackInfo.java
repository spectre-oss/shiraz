// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TvTrackInfo implements Parcelable {
    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_SUBTITLE = 2;

    public static final class Builder {
        private final int mType;
        private final String mId;
        private String mLanguage;
        private CharSequence mDescription;
        private String mEncoding;
        private boolean mEncrypted;
        private int mAudioChannelCount;
        private int mAudioSampleRate;
        private boolean mAudioDescription;
        private boolean mHardOfHearing;
        private boolean mSpokenSubtitle;
        private int mVideoWidth;
        private int mVideoHeight;
        private float mVideoFrameRate;
        private float mVideoPixelAspectRatio = 1.0f;
        private byte mVideoActiveFormatDescription;
        private Bundle mExtra;

        public Builder(int type, String id) {
            if (type != TYPE_AUDIO && type != TYPE_VIDEO && type != TYPE_SUBTITLE) {
                throw new IllegalArgumentException("Unknown type: " + type);
            }
            mType = type;
            mId = Objects.requireNonNull(id, "id cannot be null");
        }

        public Builder setLanguage(String language) {
            mLanguage = language;
            return this;
        }

        public Builder setDescription(CharSequence description) {
            mDescription = description;
            return this;
        }

        public Builder setEncoding(String encoding) {
            mEncoding = encoding;
            return this;
        }

        public Builder setEncrypted(boolean encrypted) {
            mEncrypted = encrypted;
            return this;
        }

        public Builder setAudioChannelCount(int audioChannelCount) {
            if (mType != TYPE_AUDIO) {
                throw new IllegalStateException("Not an audio track");
            }
            mAudioChannelCount = audioChannelCount;
            return this;
        }

        public Builder setAudioSampleRate(int audioSampleRate) {
            if (mType != TYPE_AUDIO) {
                throw new IllegalStateException("Not an audio track");
            }
            mAudioSampleRate = audioSampleRate;
            return this;
        }

        public Builder setAudioDescription(boolean audioDescription) {
            if (mType != TYPE_AUDIO) {
                throw new IllegalStateException("Not an audio track");
            }
            mAudioDescription = audioDescription;
            return this;
        }

        public Builder setHardOfHearing(boolean hardOfHearing) {
            if (mType != TYPE_AUDIO && mType != TYPE_SUBTITLE) {
                throw new IllegalStateException("Not an audio track or a subtitle track");
            }
            mHardOfHearing = hardOfHearing;
            return this;
        }

        public Builder setSpokenSubtitle(boolean spokenSubtitle) {
            if (mType != TYPE_AUDIO) {
                throw new IllegalStateException("Not an audio track");
            }
            mSpokenSubtitle = spokenSubtitle;
            return this;
        }

        public Builder setVideoWidth(int videoWidth) {
            if (mType != TYPE_VIDEO) {
                throw new IllegalStateException("Not a video track");
            }
            mVideoWidth = videoWidth;
            return this;
        }

        public Builder setVideoHeight(int videoHeight) {
            if (mType != TYPE_VIDEO) {
                throw new IllegalStateException("Not a video track");
            }
            mVideoHeight = videoHeight;
            return this;
        }

        public Builder setVideoFrameRate(float videoFrameRate) {
            if (mType != TYPE_VIDEO) {
                throw new IllegalStateException("Not a video track");
            }
            mVideoFrameRate = videoFrameRate;
            return this;
        }

        public Builder setVideoPixelAspectRatio(float videoPixelAspectRatio) {
            if (mType != TYPE_VIDEO) {
                throw new IllegalStateException("Not a video track");
            }
            mVideoPixelAspectRatio = videoPixelAspectRatio;
            return this;
        }

        public Builder setVideoActiveFormatDescription(byte videoActiveFormatDescription) {
            if (mType != TYPE_VIDEO) {
                throw new IllegalStateException("Not a video track");
            }
            mVideoActiveFormatDescription = videoActiveFormatDescription;
            return this;
        }

        public Builder setExtra(Bundle extra) {
            mExtra = extra != null ? new Bundle(extra) : null;
            return this;
        }

        public TvTrackInfo build() {
            return new TvTrackInfo(
                    mType, mId, mLanguage, mDescription, mEncoding, mEncrypted,
                    mAudioChannelCount, mAudioSampleRate, mAudioDescription,
                    mHardOfHearing, mSpokenSubtitle,
                    mVideoWidth, mVideoHeight, mVideoFrameRate,
                    mVideoPixelAspectRatio, mVideoActiveFormatDescription, mExtra);
        }
    }

    private final int mType;
    private final String mId;
    private final String mLanguage;
    private final CharSequence mDescription;
    private final String mEncoding;
    private final boolean mEncrypted;
    private final int mAudioChannelCount;
    private final int mAudioSampleRate;
    private final boolean mAudioDescription;
    private final boolean mHardOfHearing;
    private final boolean mSpokenSubtitle;
    private final int mVideoWidth;
    private final int mVideoHeight;
    private final float mVideoFrameRate;
    private final float mVideoPixelAspectRatio;
    private final byte mVideoActiveFormatDescription;
    private final Bundle mExtra;

    private TvTrackInfo(
            int type,
            String id,
            String language,
            CharSequence description,
            String encoding,
            boolean encrypted,
            int audioChannelCount,
            int audioSampleRate,
            boolean audioDescription,
            boolean hardOfHearing,
            boolean spokenSubtitle,
            int videoWidth,
            int videoHeight,
            float videoFrameRate,
            float videoPixelAspectRatio,
            byte videoActiveFormatDescription,
            Bundle extra) {
        mType = type;
        mId = id;
        mLanguage = language;
        mDescription = description;
        mEncoding = encoding;
        mEncrypted = encrypted;
        mAudioChannelCount = audioChannelCount;
        mAudioSampleRate = audioSampleRate;
        mAudioDescription = audioDescription;
        mHardOfHearing = hardOfHearing;
        mSpokenSubtitle = spokenSubtitle;
        mVideoWidth = videoWidth;
        mVideoHeight = videoHeight;
        mVideoFrameRate = videoFrameRate;
        mVideoPixelAspectRatio = videoPixelAspectRatio;
        mVideoActiveFormatDescription = videoActiveFormatDescription;
        mExtra = extra;
    }

    public int getType() {
        return mType;
    }

    public String getId() {
        return mId;
    }

    public String getLanguage() {
        return mLanguage;
    }

    public CharSequence getDescription() {
        return mDescription;
    }

    public String getEncoding() {
        return mEncoding;
    }

    public boolean isEncrypted() {
        return mEncrypted;
    }

    public int getAudioChannelCount() {
        if (mType != TYPE_AUDIO) {
            throw new IllegalStateException("Not an audio track");
        }
        return mAudioChannelCount;
    }

    public int getAudioSampleRate() {
        if (mType != TYPE_AUDIO) {
            throw new IllegalStateException("Not an audio track");
        }
        return mAudioSampleRate;
    }

    public boolean isAudioDescription() {
        if (mType != TYPE_AUDIO) {
            throw new IllegalStateException("Not an audio track");
        }
        return mAudioDescription;
    }

    public boolean isHardOfHearing() {
        if (mType != TYPE_AUDIO && mType != TYPE_SUBTITLE) {
            throw new IllegalStateException("Not an audio or a subtitle track");
        }
        return mHardOfHearing;
    }

    public boolean isSpokenSubtitle() {
        if (mType != TYPE_AUDIO) {
            throw new IllegalStateException("Not an audio track");
        }
        return mSpokenSubtitle;
    }

    public int getVideoWidth() {
        if (mType != TYPE_VIDEO) {
            throw new IllegalStateException("Not a video track");
        }
        return mVideoWidth;
    }

    public int getVideoHeight() {
        if (mType != TYPE_VIDEO) {
            throw new IllegalStateException("Not a video track");
        }
        return mVideoHeight;
    }

    public float getVideoFrameRate() {
        if (mType != TYPE_VIDEO) {
            throw new IllegalStateException("Not a video track");
        }
        return mVideoFrameRate;
    }

    public float getVideoPixelAspectRatio() {
        if (mType != TYPE_VIDEO) {
            throw new IllegalStateException("Not a video track");
        }
        return mVideoPixelAspectRatio;
    }

    public byte getVideoActiveFormatDescription() {
        if (mType != TYPE_VIDEO) {
            throw new IllegalStateException("Not a video track");
        }
        return mVideoActiveFormatDescription;
    }

    public Bundle getExtra() {
        return mExtra != null ? new Bundle(mExtra) : null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeString(mId);
        dest.writeString(mLanguage);
        dest.writeString(mDescription != null ? mDescription.toString() : null);
        dest.writeString(mEncoding);
        dest.writeInt(mEncrypted ? 1 : 0);
        dest.writeInt(mAudioChannelCount);
        dest.writeInt(mAudioSampleRate);
        dest.writeInt(mAudioDescription ? 1 : 0);
        dest.writeInt(mHardOfHearing ? 1 : 0);
        dest.writeInt(mSpokenSubtitle ? 1 : 0);
        dest.writeInt(mVideoWidth);
        dest.writeInt(mVideoHeight);
        dest.writeFloat(mVideoFrameRate);
        dest.writeFloat(mVideoPixelAspectRatio);
        dest.writeByte(mVideoActiveFormatDescription);
        dest.writeBundle(mExtra);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TvTrackInfo)) return false;
        TvTrackInfo obj = (TvTrackInfo) o;
        return Objects.equals(mId, obj.mId)
                && mType == obj.mType
                && Objects.equals(mLanguage, obj.mLanguage)
                && Objects.equals(
                        mDescription != null ? mDescription.toString() : null,
                        obj.mDescription != null ? obj.mDescription.toString() : null)
                && Objects.equals(mEncoding, obj.mEncoding)
                && mEncrypted == obj.mEncrypted
                && (mType != TYPE_AUDIO || (mAudioChannelCount == obj.mAudioChannelCount
                        && mAudioSampleRate == obj.mAudioSampleRate
                        && mAudioDescription == obj.mAudioDescription
                        && mHardOfHearing == obj.mHardOfHearing
                        && mSpokenSubtitle == obj.mSpokenSubtitle))
                && (mType != TYPE_SUBTITLE || mHardOfHearing == obj.mHardOfHearing)
                && (mType != TYPE_VIDEO || (mVideoWidth == obj.mVideoWidth
                        && mVideoHeight == obj.mVideoHeight
                        && Float.compare(mVideoFrameRate, obj.mVideoFrameRate) == 0
                        && Float.compare(mVideoPixelAspectRatio, obj.mVideoPixelAspectRatio) == 0
                        && mVideoActiveFormatDescription == obj.mVideoActiveFormatDescription));
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mType, mLanguage, mDescription);
    }

    public static final Parcelable.Creator<TvTrackInfo> CREATOR =
            new Parcelable.Creator<TvTrackInfo>() {
                @Override
                public TvTrackInfo createFromParcel(Parcel in) {
                    int type = in.readInt();
                    String id = in.readString();
                    String lang = in.readString();
                    String desc = in.readString();
                    String enc = in.readString();
                    boolean encrypted = in.readInt() != 0;
                    int chCount = in.readInt();
                    int sampleRate = in.readInt();
                    boolean audioDesc = in.readInt() != 0;
                    boolean hardOfHearing = in.readInt() != 0;
                    boolean spokenSub = in.readInt() != 0;
                    int vw = in.readInt();
                    int vh = in.readInt();
                    float vfr = in.readFloat();
                    float vpar = in.readFloat();
                    byte vafd = in.readByte();
                    Bundle extra = in.readBundle();
                    return new TvTrackInfo(
                            type, id, lang, desc, enc, encrypted,
                            chCount, sampleRate, audioDesc, hardOfHearing, spokenSub,
                            vw, vh, vfr, vpar, vafd, extra);
                }

                @Override
                public TvTrackInfo[] newArray(int size) {
                    return new TvTrackInfo[size];
                }
            };
}
