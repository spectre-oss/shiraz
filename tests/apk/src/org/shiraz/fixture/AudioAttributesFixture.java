// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.media.AudioAttributes;
import android.os.Bundle;

final class AudioAttributesFixture {
    static void check() {
        AudioAttributes empty = new AudioAttributes.Builder().build();
        if (empty.getUsage() != AudioAttributes.USAGE_UNKNOWN || empty.getContentType() != AudioAttributes.CONTENT_TYPE_UNKNOWN || empty.getFlags() != 0)
            throw new AssertionError("Default audio attributes");
        AudioAttributes.Builder builder = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setFlags(AudioAttributes.FLAG_LOW_LATENCY);
        AudioAttributes notification = builder.build();
        builder.setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setFlags(AudioAttributes.FLAG_HW_AV_SYNC);
        if (notification.getUsage() != AudioAttributes.USAGE_NOTIFICATION || notification.getContentType() != AudioAttributes.CONTENT_TYPE_SPEECH
                || notification.getFlags() != AudioAttributes.FLAG_LOW_LATENCY || notification.equals(builder.build()))
            throw new AssertionError("Immutable attribute snapshot");
        AudioAttributes copy = new AudioAttributes.Builder(notification).build();
        if (!notification.equals(copy) || notification.hashCode() != copy.hashCode() || notification.equals(null) || notification.equals("audio"))
            throw new AssertionError("Audio attribute value equality");
        AudioAttributes flags = new AudioAttributes.Builder().setFlags(-1).setFlags(0).build();
        if (flags.getFlags() != (AudioAttributes.FLAG_AUDIBILITY_ENFORCED | AudioAttributes.FLAG_LOW_LATENCY | AudioAttributes.FLAG_HW_AV_SYNC))
            throw new AssertionError("Public flags/masking/accumulation");
        AudioAttributes enforced = new AudioAttributes.Builder().setLegacyStreamType(7).build();
        if (enforced.getUsage() != AudioAttributes.USAGE_ASSISTANCE_SONIFICATION || enforced.getContentType() != AudioAttributes.CONTENT_TYPE_SONIFICATION
                || (enforced.getFlags() & AudioAttributes.FLAG_AUDIBILITY_ENFORCED) == 0) throw new AssertionError("Legacy stream attributes");
        if (!new AudioAttributes.Builder().setLegacyStreamType(9).build().equals(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()))
            throw new AssertionError("API 27 legacy TTS attributes");
        try { builder.setUsage(1000); throw new AssertionError("Invalid usage accepted"); } catch (IllegalArgumentException expected) { }
        try { builder.setContentType(-1); throw new AssertionError("Invalid content accepted"); } catch (IllegalArgumentException expected) { }
        try { builder.setLegacyStreamType(10); throw new AssertionError("Accessibility treated as legacy playback stream"); } catch (IllegalArgumentException expected) { }
        Bundle state = new Bundle(); state.putParcelable("attributes", notification);
        if (state.getParcelable("attributes") != notification || state.describeContents() != 0) throw new AssertionError("In-process attribute transport");
        try { notification.writeToParcel(null, 0); throw new AssertionError("Unsupported parcel audio attributes accepted"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("SHIRAZ_FIXTURE_AUDIO_ATTRIBUTES_OK");
    }
}
