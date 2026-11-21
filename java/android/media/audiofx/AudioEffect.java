// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for controlling audio effects provided by the Android audio framework.
 */
public class AudioEffect {
    public static final UUID EFFECT_TYPE_ENV_REVERB =
            UUID.fromString("c2e5d5f0-94bd-4763-9cac-4e234d06839e");
    public static final UUID EFFECT_TYPE_PRESET_REVERB =
            UUID.fromString("47382d60-ddd8-11db-bf3a-0002a5d5c51b");
    public static final UUID EFFECT_TYPE_EQUALIZER =
            UUID.fromString("0bed4300-ddd6-11db-8f34-0002a5d5c51b");
    public static final UUID EFFECT_TYPE_BASS_BOOST =
            UUID.fromString("0634f220-ddd4-11db-a0fc-0002a5d5c51b");
    public static final UUID EFFECT_TYPE_VIRTUALIZER =
            UUID.fromString("37cc2c00-dddd-11db-8577-0002a5d5c51b");
    public static final UUID EFFECT_TYPE_AGC =
            UUID.fromString("0a8abfe0-654c-11e0-ba26-0002a5d5c51b");
    public static final UUID EFFECT_TYPE_AEC =
            UUID.fromString("7b491460-8d4d-11e0-bd61-0002a5d5c51b");
    public static final UUID EFFECT_TYPE_NS =
            UUID.fromString("58b4b260-8e06-11e0-aa8e-0002a5d5c51b");
    public static final UUID EFFECT_TYPE_LOUDNESS_ENHANCER =
            UUID.fromString("fe3199be-aed0-413f-87bb-11260eb63cf1");
    public static final UUID EFFECT_TYPE_DYNAMICS_PROCESSING =
            UUID.fromString("7261676f-6d75-7369-6364-28e2fd3ac39e");
    public static final UUID EFFECT_TYPE_HAPTIC_GENERATOR =
            UUID.fromString("1411e6d6-aecd-4021-a1cf-a6aceb0d71e5");

    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int ALREADY_EXISTS = -2;
    public static final int ERROR_NO_INIT = -3;
    public static final int ERROR_BAD_VALUE = -4;
    public static final int ERROR_INVALID_OPERATION = -5;
    public static final int ERROR_NO_MEMORY = -6;
    public static final int ERROR_DEAD_OBJECT = -7;

    public static final String EFFECT_INSERT = "Insert";
    public static final String EFFECT_AUXILIARY = "Auxiliary";
    public static final String EFFECT_PRE_PROCESSING = "Pre Processing";
    public static final String EFFECT_POST_PROCESSING = "Post Processing";

    public static final String ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL =
            "android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL";
    public static final String ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION =
            "android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION";
    public static final String ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION =
            "android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION";

    public static final String EXTRA_AUDIO_SESSION = "android.media.extra.AUDIO_SESSION";
    public static final String EXTRA_PACKAGE_NAME = "android.media.extra.PACKAGE_NAME";
    public static final String EXTRA_CONTENT_TYPE = "android.media.extra.CONTENT_TYPE";

    public static final int CONTENT_TYPE_MUSIC = 0;
    public static final int CONTENT_TYPE_MOVIE = 1;
    public static final int CONTENT_TYPE_GAME = 2;
    public static final int CONTENT_TYPE_VOICE = 3;

    public static class Descriptor {
        public UUID type;
        public UUID uuid;
        public String connectMode;
        public String name;
        public String implementor;

        public Descriptor() {
        }

        public Descriptor(
                String type,
                String uuid,
                String connectMode,
                String name,
                String implementor) {
            this.type = UUID.fromString(type);
            this.uuid = UUID.fromString(uuid);
            this.connectMode = connectMode;
            this.name = name;
            this.implementor = implementor;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, uuid, connectMode, name, implementor);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Descriptor)) return false;
            Descriptor other = (Descriptor) o;
            return Objects.equals(type, other.type)
                    && Objects.equals(uuid, other.uuid)
                    && Objects.equals(connectMode, other.connectMode)
                    && Objects.equals(name, other.name)
                    && Objects.equals(implementor, other.implementor);
        }
    }

    public interface OnEnableStatusChangeListener {
        void onEnableStatusChange(AudioEffect effect, boolean enabled);
    }

    public interface OnControlStatusChangeListener {
        void onControlStatusChange(AudioEffect effect, boolean controlGranted);
    }

    private static final AtomicInteger NEXT_EFFECT_ID = new AtomicInteger(1);

    private final int mId;
    private final Descriptor mDescriptor;
    private boolean mEnabled;
    private boolean mReleased;
    private OnEnableStatusChangeListener mEnableStatusChangeListener;
    private OnControlStatusChangeListener mControlStatusChangeListener;

    protected AudioEffect(UUID type, UUID uuid, int priority, int audioSession) {
        mId = NEXT_EFFECT_ID.getAndIncrement();
        mDescriptor = new Descriptor();
        mDescriptor.type = type != null ? type : EFFECT_TYPE_EQUALIZER;
        mDescriptor.uuid = uuid != null ? uuid : mDescriptor.type;
        mDescriptor.connectMode = EFFECT_INSERT;
        mDescriptor.name = "ShirazAudioEffect";
        mDescriptor.implementor = "Shiraz";
    }

    protected final void checkInitialized() throws IllegalStateException {
        if (mReleased) {
            throw new IllegalStateException("AudioEffect has already been released");
        }
    }

    public void release() {
        mReleased = true;
    }

    @Override
    protected void finalize() {
        release();
    }

    public Descriptor getDescriptor() throws IllegalStateException {
        checkInitialized();
        return mDescriptor;
    }

    public static Descriptor[] queryEffects() {
        return new Descriptor[0];
    }

    public int setEnabled(boolean enabled) throws IllegalStateException {
        checkInitialized();
        boolean changed = (mEnabled != enabled);
        mEnabled = enabled;
        if (changed && mEnableStatusChangeListener != null) {
            mEnableStatusChangeListener.onEnableStatusChange(this, enabled);
        }
        return SUCCESS;
    }

    public int getId() throws IllegalStateException {
        checkInitialized();
        return mId;
    }

    public boolean getEnabled() throws IllegalStateException {
        checkInitialized();
        return mEnabled;
    }

    public boolean hasControl() throws IllegalStateException {
        checkInitialized();
        return true;
    }

    public void setEnableStatusListener(OnEnableStatusChangeListener listener) {
        mEnableStatusChangeListener = listener;
    }

    public void setControlStatusListener(OnControlStatusChangeListener listener) {
        mControlStatusChangeListener = listener;
    }
}
