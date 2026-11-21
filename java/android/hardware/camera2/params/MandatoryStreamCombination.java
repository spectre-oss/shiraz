// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import android.hardware.HardwareBuffer;
import android.util.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MandatoryStreamCombination {
    private final CharSequence mDescription;
    private final boolean mIsReprocessable;
    private final List<MandatoryStreamInformation> mStreamsInformation;

    public MandatoryStreamCombination(
            List<MandatoryStreamInformation> streamsInformation,
            String description,
            boolean isReprocessable) {
        if (streamsInformation == null || streamsInformation.isEmpty()) {
            throw new IllegalArgumentException("streamsInformation must be non-empty");
        }
        mStreamsInformation = Collections.unmodifiableList(new ArrayList<MandatoryStreamInformation>(streamsInformation));
        mDescription = description != null ? description : "";
        mIsReprocessable = isReprocessable;
    }

    public CharSequence getDescription() {
        return mDescription;
    }

    public boolean isReprocessable() {
        return mIsReprocessable;
    }

    public List<MandatoryStreamInformation> getStreamsInformation() {
        return mStreamsInformation;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MandatoryStreamCombination)) return false;
        MandatoryStreamCombination other = (MandatoryStreamCombination) obj;
        return mIsReprocessable == other.mIsReprocessable
                && Objects.equals(mDescription, other.mDescription)
                && Objects.equals(mStreamsInformation, other.mStreamsInformation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDescription, Boolean.valueOf(mIsReprocessable), mStreamsInformation);
    }

    public static final class MandatoryStreamInformation {
        private final boolean mIsInput;
        private final boolean mIsUltraHighResolution;
        private final boolean mIsMaximumSize;
        private final boolean mIs10BitCapable;
        private final List<Size> mAvailableSizes;
        private final int mFormat;
        private final int m10BitFormat;
        private final long mStreamUseCase;

        public MandatoryStreamInformation(
                List<Size> availableSizes,
                int format,
                boolean isInput) {
            if (availableSizes == null || availableSizes.isEmpty()) {
                throw new IllegalArgumentException("availableSizes must be non-empty");
            }
            mAvailableSizes = Collections.unmodifiableList(new ArrayList<Size>(availableSizes));
            mFormat = format;
            mIsInput = isInput;
            mIsUltraHighResolution = false;
            mIsMaximumSize = false;
            mIs10BitCapable = false;
            m10BitFormat = HardwareBuffer.YCBCR_P010;
            mStreamUseCase = 0L;
        }

        public boolean isInput() {
            return mIsInput;
        }

        public boolean isUltraHighResolution() {
            return mIsUltraHighResolution;
        }

        public boolean isMaximumSize() {
            return mIsMaximumSize;
        }

        public boolean is10BitCapable() {
            return mIs10BitCapable;
        }

        public List<Size> getAvailableSizes() {
            return mAvailableSizes;
        }

        public int getFormat() {
            return mFormat;
        }

        public int get10BitFormat() {
            if (!mIs10BitCapable) {
                throw new IllegalStateException("Stream is not 10-bit capable");
            }
            return m10BitFormat;
        }

        public long getStreamUseCase() {
            return mStreamUseCase;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MandatoryStreamInformation)) return false;
            MandatoryStreamInformation other = (MandatoryStreamInformation) obj;
            return mIsInput == other.mIsInput
                    && mIsUltraHighResolution == other.mIsUltraHighResolution
                    && mIsMaximumSize == other.mIsMaximumSize
                    && mIs10BitCapable == other.mIs10BitCapable
                    && mFormat == other.mFormat
                    && m10BitFormat == other.m10BitFormat
                    && mStreamUseCase == other.mStreamUseCase
                    && Objects.equals(mAvailableSizes, other.mAvailableSizes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Boolean.valueOf(mIsInput),
                    Boolean.valueOf(mIsUltraHighResolution),
                    Boolean.valueOf(mIsMaximumSize),
                    Boolean.valueOf(mIs10BitCapable),
                    mAvailableSizes,
                    Integer.valueOf(mFormat),
                    Integer.valueOf(m10BitFormat),
                    Long.valueOf(mStreamUseCase));
        }
    }
}
