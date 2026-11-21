// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Cooperative APK-local focus. Does not claim control of other desktop applications. */
public class AudioManager {
    public static final String ACTION_AUDIO_BECOMING_NOISY = "android.media.AUDIO_BECOMING_NOISY";
    public static final String ACTION_HDMI_AUDIO_PLUG = "android.media.action.HDMI_AUDIO_PLUG";
    public static final String ACTION_HEADSET_PLUG = "android.intent.action.HEADSET_PLUG";
    public static final String ACTION_MICROPHONE_MUTE_CHANGED = "android.media.action.MICROPHONE_MUTE_CHANGED";
    @Deprecated public static final String ACTION_SCO_AUDIO_STATE_CHANGED = "android.media.SCO_AUDIO_STATE_CHANGED";
    public static final String ACTION_SCO_AUDIO_STATE_UPDATED = "android.media.ACTION_SCO_AUDIO_STATE_UPDATED";
    public static final String ACTION_SPEAKERPHONE_STATE_CHANGED = "android.media.action.SPEAKERPHONE_STATE_CHANGED";
    public static final String RINGER_MODE_CHANGED_ACTION = "android.media.RINGER_MODE_CHANGED";
    @Deprecated public static final String VIBRATE_SETTING_CHANGED_ACTION = "android.media.VIBRATE_SETTING_CHANGED";

    public static final String EXTRA_AUDIO_PLUG_STATE = "android.media.extra.AUDIO_PLUG_STATE";
    public static final String EXTRA_ENCODINGS = "android.media.extra.ENCODINGS";
    public static final String EXTRA_MAX_CHANNEL_COUNT = "android.media.extra.MAX_CHANNEL_COUNT";
    public static final String EXTRA_RINGER_MODE = "android.media.EXTRA_RINGER_MODE";
    public static final String EXTRA_SCO_AUDIO_PREVIOUS_STATE = "android.media.extra.SCO_AUDIO_PREVIOUS_STATE";
    public static final String EXTRA_SCO_AUDIO_STATE = "android.media.extra.SCO_AUDIO_STATE";
    @Deprecated public static final String EXTRA_VIBRATE_SETTING = "android.media.EXTRA_VIBRATE_SETTING";
    @Deprecated public static final String EXTRA_VIBRATE_TYPE = "android.media.EXTRA_VIBRATE_TYPE";

    public static final int STREAM_VOICE_CALL = 0;
    public static final int STREAM_SYSTEM = 1;
    public static final int STREAM_RING = 2;
    public static final int STREAM_MUSIC = 3;
    public static final int STREAM_ALARM = 4;
    public static final int STREAM_NOTIFICATION = 5;
    public static final int STREAM_DTMF = 8;
    public static final int STREAM_ACCESSIBILITY = 10;
    @Deprecated public static final int NUM_STREAMS = 5;
    public static final int USE_DEFAULT_STREAM_TYPE = Integer.MIN_VALUE;

    public static final int ADJUST_LOWER = -1;
    public static final int ADJUST_RAISE = 1;
    public static final int ADJUST_SAME = 0;
    public static final int ADJUST_MUTE = -100;
    public static final int ADJUST_UNMUTE = 100;
    public static final int ADJUST_TOGGLE_MUTE = 101;

    public static final int FLAG_SHOW_UI = 1;
    public static final int FLAG_ALLOW_RINGER_MODES = 2;
    public static final int FLAG_PLAY_SOUND = 4;
    public static final int FLAG_REMOVE_SOUND_AND_VIBRATE = 8;
    public static final int FLAG_VIBRATE = 16;

    public static final int RINGER_MODE_SILENT = 0;
    public static final int RINGER_MODE_VIBRATE = 1;
    public static final int RINGER_MODE_NORMAL = 2;

    @Deprecated public static final int VIBRATE_TYPE_RINGER = 0;
    @Deprecated public static final int VIBRATE_TYPE_NOTIFICATION = 1;
    @Deprecated public static final int VIBRATE_SETTING_OFF = 0;
    @Deprecated public static final int VIBRATE_SETTING_ON = 1;
    @Deprecated public static final int VIBRATE_SETTING_ONLY_SILENT = 2;

    public static final int AUDIOFOCUS_NONE = 0;
    public static final int AUDIOFOCUS_GAIN = 1;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT = 2;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK = 3;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE = 4;
    public static final int AUDIOFOCUS_LOSS = -1;
    public static final int AUDIOFOCUS_LOSS_TRANSIENT = -2;
    public static final int AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3;
    public static final int AUDIOFOCUS_REQUEST_FAILED = 0;
    public static final int AUDIOFOCUS_REQUEST_GRANTED = 1;
    public static final int AUDIOFOCUS_REQUEST_DELAYED = 2;

    public static final int AUDIO_SESSION_ID_GENERATE = 0;
    public static final int ERROR = -1;
    public static final int ERROR_DEAD_OBJECT = -6;

    public static final int MODE_INVALID = -2;
    public static final int MODE_CURRENT = -1;
    public static final int MODE_NORMAL = 0;
    public static final int MODE_RINGTONE = 1;
    public static final int MODE_IN_CALL = 2;
    public static final int MODE_IN_COMMUNICATION = 3;
    public static final int MODE_CALL_SCREENING = 4;
    public static final int MODE_CALL_REDIRECT = 5;
    public static final int MODE_COMMUNICATION_REDIRECT = 6;

    @Deprecated public static final int ROUTE_EARPIECE = 1;
    @Deprecated public static final int ROUTE_SPEAKER = 2;
    @Deprecated public static final int ROUTE_BLUETOOTH = 4;
    @Deprecated public static final int ROUTE_BLUETOOTH_SCO = 4;
    @Deprecated public static final int ROUTE_HEADSET = 8;
    @Deprecated public static final int ROUTE_BLUETOOTH_A2DP = 16;
    @Deprecated public static final int ROUTE_ALL = -1;

    public static final int SCO_AUDIO_STATE_DISCONNECTED = 0;
    public static final int SCO_AUDIO_STATE_CONNECTED = 1;
    public static final int SCO_AUDIO_STATE_CONNECTING = 2;
    public static final int SCO_AUDIO_STATE_ERROR = -1;

    public static final int FX_KEY_CLICK = 0;
    public static final int FX_FOCUS_NAVIGATION_UP = 1;
    public static final int FX_FOCUS_NAVIGATION_DOWN = 2;
    public static final int FX_FOCUS_NAVIGATION_LEFT = 3;
    public static final int FX_FOCUS_NAVIGATION_RIGHT = 4;
    public static final int FX_KEYPRESS_STANDARD = 5;
    public static final int FX_KEYPRESS_SPACEBAR = 6;
    public static final int FX_KEYPRESS_DELETE = 7;
    public static final int FX_KEYPRESS_RETURN = 8;
    public static final int FX_KEYPRESS_INVALID = 9;
    public static final int FX_BACK = 10;

    public static final int GET_DEVICES_INPUTS = 1;
    public static final int GET_DEVICES_OUTPUTS = 2;
    public static final int GET_DEVICES_ALL = 3;

    public static final String PROPERTY_OUTPUT_SAMPLE_RATE = "android.media.property.OUTPUT_SAMPLE_RATE";
    public static final String PROPERTY_OUTPUT_FRAMES_PER_BUFFER = "android.media.property.OUTPUT_FRAMES_PER_BUFFER";
    public static final String PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND = "android.media.property.SUPPORT_MIC_NEAR_ULTRASOUND";
    public static final String PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND = "android.media.property.SUPPORT_SPEAKER_NEAR_ULTRASOUND";
    public static final String PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED = "android.media.property.SUPPORT_AUDIO_SOURCE_UNPROCESSED";

    public static final int ENCODED_SURROUND_OUTPUT_UNKNOWN = -1;
    public static final int ENCODED_SURROUND_OUTPUT_AUTO = 0;
    public static final int ENCODED_SURROUND_OUTPUT_NEVER = 1;
    public static final int ENCODED_SURROUND_OUTPUT_ALWAYS = 2;
    public static final int ENCODED_SURROUND_OUTPUT_MANUAL = 3;

    public static final int PLAYBACK_OFFLOAD_NOT_SUPPORTED = 0;
    public static final int PLAYBACK_OFFLOAD_SUPPORTED = 1;
    public static final int PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED = 2;

    public static final int DIRECT_PLAYBACK_NOT_SUPPORTED = 0;
    public static final int DIRECT_PLAYBACK_OFFLOAD_SUPPORTED = 1;
    public static final int DIRECT_PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED = 3;
    public static final int DIRECT_PLAYBACK_BITSTREAM_SUPPORTED = 4;

    public interface OnAudioFocusChangeListener {
        void onAudioFocusChange(int focusChange);
    }

    public abstract static class AudioPlaybackCallback {
        public AudioPlaybackCallback() {}
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {}
    }

    public abstract static class AudioRecordingCallback {
        public AudioRecordingCallback() {}
        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {}
    }

    public interface OnCommunicationDeviceChangedListener {
        void onCommunicationDeviceChanged(AudioDeviceInfo device);
    }

    public interface OnModeChangedListener {
        void onModeChanged(int mode);
    }

    public interface OnPreferredMixerAttributesChangedListener {
        void onPreferredMixerAttributesChanged(
                AudioAttributes attributes,
                AudioDeviceInfo device,
                AudioMixerAttributes mixerAttributes);
    }

    private final ArrayList<AudioFocusRequest> owners = new ArrayList<AudioFocusRequest>();
    private final IdentityHashMap<AudioDeviceCallback, Handler> deviceCallbacks =
            new IdentityHashMap<AudioDeviceCallback, Handler>();
    private final ConcurrentHashMap<AudioPlaybackCallback, Boolean> playbackCallbacks =
            new ConcurrentHashMap<AudioPlaybackCallback, Boolean>();
    private final ConcurrentHashMap<AudioRecordingCallback, Boolean> recordingCallbacks =
            new ConcurrentHashMap<AudioRecordingCallback, Boolean>();
    private final ConcurrentHashMap<OnModeChangedListener, Executor> modeListeners =
            new ConcurrentHashMap<OnModeChangedListener, Executor>();
    private final ConcurrentHashMap<OnCommunicationDeviceChangedListener, Executor> commListeners =
            new ConcurrentHashMap<OnCommunicationDeviceChangedListener, Executor>();
    private final ConcurrentHashMap<OnPreferredMixerAttributesChangedListener, Executor> mixerListeners =
            new ConcurrentHashMap<OnPreferredMixerAttributesChangedListener, Executor>();
    private final Spatializer spatializer = new Spatializer();
    private int ringerMode = RINGER_MODE_NORMAL;
    private int allowedCapturePolicy = 1;
    private int encodedSurroundMode = ENCODED_SURROUND_OUTPUT_AUTO;
    private boolean speakerphoneOn = true;
    private boolean microphoneMute;
    private int nextSessionId = 1;

    private AudioManager() {}

    public static AudioManager shirazForProcess() {
        return new AudioManager();
    }

    public void dispatchMediaKeyEvent(KeyEvent keyEvent) {
        Objects.requireNonNull(keyEvent, "keyEvent");
    }

    /** Optional system UI cues are muted: Shiraz has no configured desktop sound-effect theme. */
    public void playSoundEffect(int effectType) {}

    public void playSoundEffect(int effectType, float volume) {}

    public void loadSoundEffects() {}

    public void unloadSoundEffects() {}

    private void notifyChange(final AudioFocusRequest request, final int change) {
        if (request.listener == null) return;
        Handler handler = request.handler;
        if (handler == null) {
            Looper main = Looper.getMainLooper();
            if (main == null) throw new IllegalStateException("Audio focus needs a main Looper");
            handler = new Handler(main);
        }
        if (!handler.post(new Runnable() {
            public void run() {
                request.listener.onAudioFocusChange(change);
            }
        })) {
            throw new IllegalStateException("Audio focus callback looper stopped");
        }
    }

    public synchronized int requestAudioFocus(AudioFocusRequest request) {
        if (request == null) throw new NullPointerException("request");
        AudioFocusRequest previous = owners.isEmpty() ? null : owners.get(owners.size() - 1);
        if (previous != null && previous.listener == request.listener && previous.gain == request.gain) {
            owners.set(owners.size() - 1, request);
            return AUDIOFOCUS_REQUEST_GRANTED;
        }
        if (owners.size() >= 64) return AUDIOFOCUS_REQUEST_FAILED;
        for (int i = owners.size() - 1; i >= 0; i--) {
            if (owners.get(i).listener == request.listener) owners.remove(i);
        }
        if (request.gain == AUDIOFOCUS_GAIN) {
            for (AudioFocusRequest owner : owners) notifyChange(owner, AUDIOFOCUS_LOSS);
            owners.clear();
        } else if (previous != null && previous.listener != request.listener) {
            notifyChange(
                    previous,
                    request.gain == AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK && !previous.pause
                            ? AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                            : AUDIOFOCUS_LOSS_TRANSIENT);
        }
        owners.add(request);
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    public int requestAudioFocus(OnAudioFocusChangeListener listener, int stream, int gain) {
        if (stream < 0 || stream > 10) throw new IllegalArgumentException("stream");
        AudioFocusRequest.Builder b = new AudioFocusRequest.Builder(gain);
        if (listener != null) b.setOnAudioFocusChangeListener(listener);
        return requestAudioFocus(b.build());
    }

    public int abandonAudioFocusRequest(AudioFocusRequest request) {
        if (request == null) throw new NullPointerException("request");
        return abandonAudioFocus(request.listener);
    }

    public synchronized int abandonAudioFocus(OnAudioFocusChangeListener listener) {
        boolean top = !owners.isEmpty() && owners.get(owners.size() - 1).listener == listener;
        for (int i = owners.size() - 1; i >= 0; i--) {
            if (owners.get(i).listener == listener) owners.remove(i);
        }
        if (top && !owners.isEmpty()) notifyChange(owners.get(owners.size() - 1), AUDIOFOCUS_GAIN);
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    public AudioDeviceInfo[] getDevices(int flags) {
        if ((flags & GET_DEVICES_OUTPUTS) != 0 && (flags & GET_DEVICES_INPUTS) != 0) {
            return new AudioDeviceInfo[] { new AudioDeviceInfo(false), new AudioDeviceInfo(true) };
        }
        if ((flags & GET_DEVICES_OUTPUTS) != 0) {
            return new AudioDeviceInfo[] { new AudioDeviceInfo(false) };
        }
        return new AudioDeviceInfo[0];
    }

    public void registerAudioDeviceCallback(final AudioDeviceCallback callback, Handler handler) {
        if (callback == null) throw new IllegalArgumentException("callback");
        final Handler target = handler == null ? new Handler(Looper.getMainLooper()) : handler;
        synchronized (deviceCallbacks) {
            deviceCallbacks.put(callback, target);
        }
        target.post(new Runnable() {
            public void run() {
                synchronized (deviceCallbacks) {
                    if (deviceCallbacks.get(callback) != target) return;
                }
                callback.onAudioDevicesAdded(getDevices(GET_DEVICES_ALL));
            }
        });
    }

    public void unregisterAudioDeviceCallback(AudioDeviceCallback callback) {
        synchronized (deviceCallbacks) {
            deviceCallbacks.remove(callback);
        }
    }

    public String getProperty(String key) {
        if (PROPERTY_OUTPUT_SAMPLE_RATE.equals(key)) return "48000";
        if (PROPERTY_OUTPUT_FRAMES_PER_BUFFER.equals(key)) return "1024";
        if (PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND.equals(key)
                || PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND.equals(key)
                || PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED.equals(key)) {
            return "false";
        }
        return null;
    }

    /** Conservative output latency in milliseconds, matching the native playback buffer. */
    public int getOutputLatency(int stream) {
        return 80;
    }

    public void setParameters(String keyValuePairs) {}

    public String getParameters(String keys) {
        return "";
    }

    public boolean isVolumeFixed() {
        return true;
    }

    public void adjustStreamVolume(int streamType, int direction, int flags) {}

    public void adjustVolume(int direction, int flags) {}

    public void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags) {}

    public int getRingerMode() {
        return ringerMode;
    }

    public void setRingerMode(int ringerMode) {
        if (ringerMode < RINGER_MODE_SILENT || ringerMode > RINGER_MODE_NORMAL) {
            throw new IllegalArgumentException("Invalid ringer mode: " + ringerMode);
        }
        this.ringerMode = ringerMode;
    }

    public boolean isRampingRingerEnabled() {
        return false;
    }

    public int getStreamVolume(int stream) {
        return 1;
    }

    public int getStreamMaxVolume(int stream) {
        return 1;
    }

    public int getStreamMinVolume(int stream) {
        return 0;
    }

    public float getStreamVolumeDb(int streamType, int index, int deviceType) {
        return index <= 0 ? Float.NEGATIVE_INFINITY : 0.0f;
    }

    public void setStreamVolume(int streamType, int index, int flags) {}

    public int getVolumeGroupIdForAttributes(AudioAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes");
        return 0;
    }

    public void adjustVolumeGroupVolume(int groupId, int direction, int flags) {}

    public boolean isVolumeGroupMuted(int groupId) {
        return false;
    }

    @Deprecated
    public void setStreamSolo(int streamType, boolean state) {}

    @Deprecated
    public void setStreamMute(int streamType, boolean state) {}

    public boolean isStreamMute(int stream) {
        return false;
    }

    @Deprecated
    public boolean shouldVibrate(int vibrateType) {
        return false;
    }

    @Deprecated
    public int getVibrateSetting(int vibrateType) {
        return VIBRATE_SETTING_OFF;
    }

    @Deprecated
    public void setVibrateSetting(int vibrateType, int vibrateSetting) {}

    public void setSpeakerphoneOn(boolean on) {
        speakerphoneOn = on;
    }

    public boolean isSpeakerphoneOn() {
        return speakerphoneOn;
    }

    public void setAllowedCapturePolicy(int capturePolicy) {
        allowedCapturePolicy = capturePolicy;
    }

    public int getAllowedCapturePolicy() {
        return allowedCapturePolicy;
    }

    public static int getDirectPlaybackSupport(AudioFormat format, AudioAttributes attributes) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(attributes, "attributes");
        return DIRECT_PLAYBACK_NOT_SUPPORTED;
    }

    public static boolean isOffloadedPlaybackSupported(AudioFormat format, AudioAttributes attributes) {
        return false;
    }

    public static int getPlaybackOffloadSupport(AudioFormat format, AudioAttributes attributes) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(attributes, "attributes");
        return PLAYBACK_OFFLOAD_NOT_SUPPORTED;
    }

    public Spatializer getSpatializer() {
        return spatializer;
    }

    public synchronized int generateAudioSessionId() {
        int id = nextSessionId++;
        if (nextSessionId <= 0) nextSessionId = 1;
        return id;
    }

    public boolean isBluetoothScoAvailableOffCall() {
        return false;
    }

    public boolean isBluetoothScoOn() {
        return false;
    }

    public void setBluetoothScoOn(boolean enabled) {
        if (enabled) throw new UnsupportedOperationException("Bluetooth SCO routing is unavailable");
    }

    public void startBluetoothSco() {
        throw new UnsupportedOperationException("Bluetooth SCO routing is unavailable");
    }

    public void stopBluetoothSco() {}

    @Deprecated
    public void setBluetoothA2dpOn(boolean on) {}

    @Deprecated
    public boolean isBluetoothA2dpOn() {
        return false;
    }

    @Deprecated
    public void setWiredHeadsetOn(boolean on) {}

    @Deprecated
    public boolean isWiredHeadsetOn() {
        return false;
    }

    public void setMicrophoneMute(boolean on) {
        microphoneMute = on;
    }

    public boolean isMicrophoneMute() {
        return microphoneMute;
    }

    public int getMode() {
        return MODE_NORMAL;
    }

    public void setMode(int mode) {
        if (mode != MODE_NORMAL) {
            throw new UnsupportedOperationException("Telephony audio modes are unavailable");
        }
    }

    public void addOnModeChangedListener(Executor executor, OnModeChangedListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        modeListeners.put(listener, executor);
    }

    public void removeOnModeChangedListener(OnModeChangedListener listener) {
        Objects.requireNonNull(listener, "listener");
        modeListeners.remove(listener);
    }

    public boolean isCallScreeningModeSupported() {
        return false;
    }

    @Deprecated
    public void setRouting(int mode, int routes, int mask) {}

    @Deprecated
    public int getRouting(int mode) {
        return -1;
    }

    public boolean isMusicActive() {
        return false;
    }

    @Deprecated
    public void registerMediaButtonEventReceiver(ComponentName eventReceiver) {}

    @Deprecated
    public void registerMediaButtonEventReceiver(PendingIntent eventReceiver) {}

    @Deprecated
    public void unregisterMediaButtonEventReceiver(ComponentName eventReceiver) {}

    @Deprecated
    public void unregisterMediaButtonEventReceiver(PendingIntent eventReceiver) {}

    @Deprecated
    public void registerRemoteControlClient(RemoteControlClient rcClient) {}

    @Deprecated
    public void unregisterRemoteControlClient(RemoteControlClient rcClient) {}

    @Deprecated
    public boolean registerRemoteController(RemoteController rctlr) {
        return false;
    }

    @Deprecated
    public void unregisterRemoteController(RemoteController rctlr) {}

    public void registerAudioPlaybackCallback(AudioPlaybackCallback cb, Handler handler) {
        Objects.requireNonNull(cb, "cb");
        playbackCallbacks.put(cb, Boolean.TRUE);
    }

    public void unregisterAudioPlaybackCallback(AudioPlaybackCallback cb) {
        if (cb != null) playbackCallbacks.remove(cb);
    }

    public List<AudioPlaybackConfiguration> getActivePlaybackConfigurations() {
        return Collections.emptyList();
    }

    public void registerAudioRecordingCallback(AudioRecordingCallback cb, Handler handler) {
        Objects.requireNonNull(cb, "cb");
        recordingCallbacks.put(cb, Boolean.TRUE);
    }

    public void unregisterAudioRecordingCallback(AudioRecordingCallback cb) {
        if (cb != null) recordingCallbacks.remove(cb);
    }

    public List<AudioRecordingConfiguration> getActiveRecordingConfigurations() {
        return Collections.emptyList();
    }

    public List<AudioDeviceInfo> getAudioDevicesForAttributes(AudioAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes");
        return Collections.singletonList(new AudioDeviceInfo(false));
    }

    public Set<Integer> getSupportedDeviceTypes(int direction) {
        Set<Integer> types = new LinkedHashSet<Integer>();
        types.add(Integer.valueOf(AudioDeviceInfo.TYPE_UNKNOWN));
        return Collections.unmodifiableSet(types);
    }

    public List<MicrophoneInfo> getMicrophones() throws IOException {
        return Collections.emptyList();
    }

    public boolean setEncodedSurroundMode(int mode) {
        encodedSurroundMode = mode;
        return true;
    }

    public int getEncodedSurroundMode() {
        return encodedSurroundMode;
    }

    public boolean setSurroundFormatEnabled(int audioFormat, boolean enabled) {
        return false;
    }

    public boolean isSurroundFormatEnabled(int audioFormat) {
        return false;
    }

    public static boolean isHapticPlaybackSupported() {
        return false;
    }

    public int getAudioHwSyncForSession(int sessionId) {
        return ERROR;
    }

    public boolean setCommunicationDevice(AudioDeviceInfo device) {
        Objects.requireNonNull(device, "device");
        return false;
    }

    public void clearCommunicationDevice() {}

    public AudioDeviceInfo getCommunicationDevice() {
        return new AudioDeviceInfo(false);
    }

    public List<AudioDeviceInfo> getAvailableCommunicationDevices() {
        return Collections.singletonList(new AudioDeviceInfo(false));
    }

    public List<AudioProfile> getDirectProfilesForAttributes(AudioAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes");
        return Collections.emptyList();
    }

    public void addOnCommunicationDeviceChangedListener(
            Executor executor, OnCommunicationDeviceChangedListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        commListeners.put(listener, executor);
    }

    public void removeOnCommunicationDeviceChangedListener(
            OnCommunicationDeviceChangedListener listener) {
        Objects.requireNonNull(listener, "listener");
        commListeners.remove(listener);
    }

    public List<AudioMixerAttributes> getSupportedMixerAttributes(AudioDeviceInfo device) {
        Objects.requireNonNull(device, "device");
        return Collections.emptyList();
    }

    public boolean setPreferredMixerAttributes(
            AudioAttributes attributes,
            AudioDeviceInfo device,
            AudioMixerAttributes mixerAttributes) {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(mixerAttributes, "mixerAttributes");
        return false;
    }

    public AudioMixerAttributes getPreferredMixerAttributes(
            AudioAttributes attributes, AudioDeviceInfo device) {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(device, "device");
        return null;
    }

    public boolean clearPreferredMixerAttributes(
            AudioAttributes attributes, AudioDeviceInfo device) {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(device, "device");
        return false;
    }

    public void addOnPreferredMixerAttributesChangedListener(
            Executor executor, OnPreferredMixerAttributesChangedListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        mixerListeners.put(listener, executor);
    }

    public void removeOnPreferredMixerAttributesChangedListener(
            OnPreferredMixerAttributesChangedListener listener) {
        Objects.requireNonNull(listener, "listener");
        mixerListeners.remove(listener);
    }
}
