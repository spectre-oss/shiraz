// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2.params;

import android.graphics.ColorSpace;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class SessionConfiguration implements Parcelable {
    public static final int SESSION_REGULAR = 0;
    public static final int SESSION_HIGH_SPEED = 1;

    private final int mSessionType;
    private final List<OutputConfiguration> mOutputConfigurations;
    private CameraCaptureSession.StateCallback mStateCallback;
    private Executor mExecutor;
    private InputConfiguration mInputConfig;
    private CaptureRequest mSessionParameters;
    private ColorSpace.Named mColorSpace;

    public SessionConfiguration(
            int sessionType,
            List<OutputConfiguration> outputs,
            Executor executor,
            CameraCaptureSession.StateCallback cb) {
        if (outputs == null) {
            throw new NullPointerException("outputs must not be null");
        }
        mSessionType = sessionType;
        mOutputConfigurations = Collections.unmodifiableList(new ArrayList<OutputConfiguration>(outputs));
        mStateCallback = cb;
        mExecutor = executor;
    }

    public SessionConfiguration(int sessionType, List<OutputConfiguration> outputs) {
        this(sessionType, outputs, null, null);
    }

    private SessionConfiguration(Parcel source) {
        mSessionType = source.readInt();
        int inputWidth = source.readInt();
        int inputHeight = source.readInt();
        int inputFormat = source.readInt();
        if (inputWidth > 0 && inputHeight > 0) {
            mInputConfig = new InputConfiguration(inputWidth, inputHeight, inputFormat);
        }
        int count = source.readInt();
        List<OutputConfiguration> list = new ArrayList<OutputConfiguration>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            list.add(OutputConfiguration.CREATOR.createFromParcel(source));
        }
        mOutputConfigurations = Collections.unmodifiableList(list);
        int csOrdinal = source.readInt();
        ColorSpace.Named[] named = ColorSpace.Named.values();
        if (csOrdinal >= 0 && csOrdinal < named.length) {
            mColorSpace = named[csOrdinal];
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (dest == null) {
            throw new IllegalArgumentException("dest must not be null");
        }
        dest.writeInt(mSessionType);
        if (mInputConfig != null) {
            dest.writeInt(mInputConfig.getWidth());
            dest.writeInt(mInputConfig.getHeight());
            dest.writeInt(mInputConfig.getFormat());
        } else {
            dest.writeInt(-1);
            dest.writeInt(-1);
            dest.writeInt(-1);
        }
        dest.writeInt(mOutputConfigurations.size());
        for (OutputConfiguration cfg : mOutputConfigurations) {
            cfg.writeToParcel(dest, flags);
        }
        dest.writeInt(mColorSpace != null ? mColorSpace.ordinal() : -1);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SessionConfiguration)) return false;
        SessionConfiguration other = (SessionConfiguration) obj;
        return mSessionType == other.mSessionType
                && Objects.equals(mInputConfig, other.mInputConfig)
                && Objects.equals(mOutputConfigurations, other.mOutputConfigurations)
                && mColorSpace == other.mColorSpace;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mSessionType),
                mInputConfig,
                mOutputConfigurations,
                mColorSpace);
    }

    public int getSessionType() {
        return mSessionType;
    }

    public List<OutputConfiguration> getOutputConfigurations() {
        return mOutputConfigurations;
    }

    public CameraCaptureSession.StateCallback getStateCallback() {
        return mStateCallback;
    }

    public Executor getExecutor() {
        return mExecutor;
    }

    public void setInputConfiguration(InputConfiguration input) {
        if (mSessionType == SESSION_HIGH_SPEED) {
            throw new UnsupportedOperationException("Input configuration is not supported for high speed sessions");
        }
        mInputConfig = input;
    }

    public InputConfiguration getInputConfiguration() {
        return mInputConfig;
    }

    public void setSessionParameters(CaptureRequest params) {
        mSessionParameters = params;
    }

    public CaptureRequest getSessionParameters() {
        return mSessionParameters;
    }

    public void setColorSpace(ColorSpace.Named colorSpace) {
        mColorSpace = colorSpace;
    }

    public void clearColorSpace() {
        mColorSpace = null;
    }

    public ColorSpace getColorSpace() {
        return mColorSpace != null ? ColorSpace.get(mColorSpace) : null;
    }

    public void setStateCallback(Executor executor, CameraCaptureSession.StateCallback cb) {
        mExecutor = executor;
        mStateCallback = cb;
    }

    public static final Parcelable.Creator<SessionConfiguration> CREATOR =
            new Parcelable.Creator<SessionConfiguration>() {
                @Override
                public SessionConfiguration createFromParcel(Parcel source) {
                    return new SessionConfiguration(source);
                }

                @Override
                public SessionConfiguration[] newArray(int size) {
                    return new SessionConfiguration[size];
                }
            };
}
