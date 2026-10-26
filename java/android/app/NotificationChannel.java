// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.os.Parcel;
import android.os.Parcelable;
import java.io.*;
import java.util.Arrays;

/** Channel policy values; device presentation and IPC require separate bridges. */
public final class NotificationChannel implements Parcelable {
    public static final String DEFAULT_CHANNEL_ID = "miscellaneous";
    private final String id;
    private String name, description, group;
    private int importance, color, visibility = -1000;
    private boolean lights, vibration, badge = true, bypass, bubbles, blockable;
    private long[] pattern;
    private android.net.Uri sound=android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
    private android.media.AudioAttributes soundAttributes=Notification.AUDIO_ATTRIBUTES_DEFAULT;
    private static String trim(CharSequence value) { if (value == null) return null; String text = value.toString(); return text.length() <= 1000 ? text : text.substring(0, 1000); }
    public NotificationChannel(String id, CharSequence name, int importance) { this.id = trim(id); this.name = trim(name); this.importance = importance; }
    private NotificationChannel(NotificationChannel source) {
        id = source.id; name = source.name; description = source.description; group = source.group;
        importance = source.importance; color = source.color; visibility = source.visibility;
        lights = source.lights; vibration = source.vibration; badge = source.badge; bypass = source.bypass;
        bubbles = source.bubbles; blockable = source.blockable; sound = source.sound; soundAttributes = source.soundAttributes; pattern = source.pattern == null ? null : source.pattern.clone();
    }
    public String getId() { return id; }
    public void setSound(android.net.Uri sound, android.media.AudioAttributes attributes) { this.sound = sound; this.soundAttributes = attributes; }
    public android.net.Uri getSound() { return sound; }
    public android.media.AudioAttributes getAudioAttributes() { return soundAttributes; }
    public CharSequence getName() { return name; }
    public String getDescription() { return description; }
    public int getImportance() { return importance; }
    public void setName(CharSequence value) { name = trim(value); }
    public void setDescription(String value) { description = trim(value); }
    public void setImportance(int value) { importance = value; }
    public String getGroup() { return group; }
    public void setGroup(String value) { group = trim(value); }
    public void enableLights(boolean value) { lights = value; }
    public boolean shouldShowLights() { return lights; }
    public void setLightColor(int value) { color = value; }
    public int getLightColor() { return color; }
    public void enableVibration(boolean value) { vibration = value; }
    public boolean shouldVibrate() { return vibration; }
    public void setVibrationPattern(long[] value) { if (value != null && value.length > 1024) throw new IllegalArgumentException("Vibration pattern exceeds host limit"); pattern = value == null ? null : value.clone(); vibration = value != null && value.length > 0; }
    public long[] getVibrationPattern() { return pattern == null ? null : pattern.clone(); }
    public void setShowBadge(boolean value) { badge = value; }
    public boolean canShowBadge() { return badge; }
    public void setBypassDnd(boolean value) { bypass = value; }
    public boolean canBypassDnd() { return bypass; }
    public void setLockscreenVisibility(int value) { visibility = value; }
    public int getLockscreenVisibility() { return visibility; }
    public void setAllowBubbles(boolean value) { bubbles = value; }
    public boolean canBubble() { return bubbles; }
    public void setBlockable(boolean value) { blockable = value; }
    public boolean isBlockable() { return blockable; }
    NotificationChannel copy() { return new NotificationChannel(this); }
    void validate() {
        if (id == null || id.isEmpty() || DEFAULT_CHANNEL_ID.equals(id) || name == null || name.isEmpty()) throw new IllegalArgumentException("Channel needs a nonreserved ID and name");
        if (importance < 0 || importance > 5) throw new IllegalArgumentException("Invalid notification importance");
        if (group != null) throw new IllegalArgumentException("NotificationChannelGroup does not exist");
    }
    byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(1); string(out, id); string(out, name); string(out, description); string(out, group);
            out.writeInt(importance); out.writeInt(color); out.writeInt(visibility);
            out.writeBoolean(lights); out.writeBoolean(vibration); out.writeBoolean(badge); out.writeBoolean(bypass); out.writeBoolean(bubbles); out.writeBoolean(blockable);
            out.writeInt(pattern == null ? -1 : pattern.length); if (pattern != null) for (long value : pattern) out.writeLong(value);
            out.flush(); return bytes.toByteArray();
        } catch (IOException error) { throw new IllegalStateException("Cannot encode channel policy", error); }
    }
    private static void string(DataOutputStream out, String value) throws IOException { out.writeBoolean(value != null); if (value != null) out.writeUTF(value); }
    private static String string(DataInputStream in) throws IOException { String value = in.readBoolean() ? in.readUTF() : null; if (value != null && value.length() > 1000) throw new IOException("Oversized channel text"); return value; }
    static NotificationChannel decode(byte[] bytes) {
        if (bytes == null || bytes.length > 32768) throw new IllegalStateException("Invalid channel policy size");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 1) throw new IOException("Unsupported channel policy version");
            String id = string(in), name = string(in), description = string(in), group = string(in);
            NotificationChannel value = new NotificationChannel(id, name, in.readInt());
            value.description = description; value.group = group; value.color = in.readInt(); value.visibility = in.readInt();
            value.lights = in.readBoolean(); value.vibration = in.readBoolean(); value.badge = in.readBoolean(); value.bypass = in.readBoolean(); value.bubbles = in.readBoolean(); value.blockable = in.readBoolean();
            int size = in.readInt(); if (size < -1 || size > 1024) throw new IOException("Invalid vibration pattern size");
            if (size >= 0) { value.pattern = new long[size]; for (int i = 0; i < size; ++i) value.pattern[i] = in.readLong(); }
            if (in.read() != -1) throw new IOException("Trailing channel policy bytes");
            value.validate(); return value;
        } catch (IOException | IllegalArgumentException error) { throw new IllegalStateException("Invalid persisted channel policy", error); }
    }
    @Override public boolean equals(Object other) { return other instanceof NotificationChannel && Arrays.equals(encode(), ((NotificationChannel)other).encode()); }
    @Override public int hashCode() { return Arrays.hashCode(encode()); }
    @Override public String toString() { return "NotificationChannel{" + id + ", importance=" + importance + "}"; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("NotificationChannel IPC is not implemented"); }
    public static final Parcelable.Creator<NotificationChannel> CREATOR = new Parcelable.Creator<NotificationChannel>() {
        public NotificationChannel createFromParcel(Parcel in) { throw new UnsupportedOperationException("NotificationChannel parcel decoding is not implemented"); }
        public NotificationChannel[] newArray(int size) { return new NotificationChannel[size]; }
    };
}
