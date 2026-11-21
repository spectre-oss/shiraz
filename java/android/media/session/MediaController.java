// SPDX-License-Identifier: GPL-3.0-only
package android.media.session;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaMetadata;
import android.media.Rating;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Controller for a session hosted in this runtime process. */
public final class MediaController {
    private final MediaSession.Token token;
    private final MediaSession session;
    private final TransportControls controls = new TransportControls();
    private int currentVolume = 15;

    public MediaController(Context context, MediaSession.Token token) {
        if (context == null || token == null) {
            throw new IllegalArgumentException("context and token required");
        }
        this.token = token;
        this.session = token.session;
    }

    public MediaSession.Token getSessionToken() {
        return token;
    }

    public String getPackageName() {
        return session.packageName();
    }

    public Bundle getSessionInfo() {
        return new Bundle();
    }

    public String getTag() {
        return "";
    }

    public PlaybackState getPlaybackState() {
        return session.playbackState;
    }

    public List<MediaSession.QueueItem> getQueue() {
        return session.queue == null ? null : new ArrayList<MediaSession.QueueItem>(session.queue);
    }

    public CharSequence getQueueTitle() {
        return session.queueTitle;
    }

    public Bundle getExtras() {
        return session.extras == null ? null : new Bundle(session.extras);
    }

    public int getRatingType() {
        return session.ratingType;
    }

    public MediaMetadata getMetadata() {
        return session.metadata;
    }

    public long getFlags() {
        return session.flags;
    }

    public PlaybackInfo getPlaybackInfo() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        return new PlaybackInfo(PlaybackInfo.PLAYBACK_TYPE_LOCAL, 2, 15, currentVolume, attrs, null);
    }

    public PendingIntent getSessionActivity() {
        return session.activity;
    }

    public TransportControls getTransportControls() {
        return controls;
    }

    public void setVolumeTo(int value, int flags) {
        if (value < 0) {
            currentVolume = 0;
        } else if (value > 15) {
            currentVolume = 15;
        } else {
            currentVolume = value;
        }
    }

    public void adjustVolume(int direction, int flags) {
        if (direction > 0 && currentVolume < 15) {
            currentVolume++;
        } else if (direction < 0 && currentVolume > 0) {
            currentVolume--;
        }
    }

    public boolean controlsSameSession(MediaController other) {
        return other != null && other.session == session;
    }

    public void registerCallback(Callback callback) {
        registerCallback(callback, null);
    }

    public void registerCallback(final Callback callback, Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
        Handler delivery = handler == null ? new Handler() : handler;
        if (session.released()) {
            delivery.post(new Runnable() {
                public void run() {
                    callback.onSessionDestroyed();
                }
            });
        } else {
            session.controllers.put(callback, delivery);
        }
    }

    public void unregisterCallback(Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
        session.controllers.remove(callback);
    }

    public void sendCommand(final String command, Bundle args, final ResultReceiver receiver) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command");
        }
        final Bundle copy = args == null ? null : new Bundle(args);
        session.dispatch(new MediaSession.Action() {
            public void accept(MediaSession.Callback c) {
                c.onCommand(command, copy, receiver);
            }
        });
    }

    public boolean dispatchMediaButtonEvent(KeyEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event");
        }
        if (session.released()) {
            return false;
        }
        final Intent intent = new Intent("android.intent.action.MEDIA_BUTTON");
        intent.putExtra("android.intent.extra.KEY_EVENT", event);
        session.dispatch(new MediaSession.Action() {
            public void accept(MediaSession.Callback c) {
                c.onMediaButtonEvent(intent);
            }
        });
        return true;
    }

    public abstract static class Callback {
        public Callback() {
        }

        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
        }

        public void onQueueTitleChanged(CharSequence title) {
        }

        public void onMetadataChanged(MediaMetadata metadata) {
        }

        public void onSessionDestroyed() {
        }

        public void onPlaybackStateChanged(PlaybackState state) {
        }

        public void onExtrasChanged(Bundle extras) {
        }

        public void onSessionEvent(String event, Bundle extras) {
        }

        public void onAudioInfoChanged(PlaybackInfo info) {
        }
    }

    public static final class PlaybackInfo implements Parcelable {
        public static final int PLAYBACK_TYPE_LOCAL = 1;
        public static final int PLAYBACK_TYPE_REMOTE = 2;

        private final int mVolumeType;
        private final int mVolumeControl;
        private final int mMaxVolume;
        private final int mCurrentVolume;
        private final AudioAttributes mAudioAttrs;
        private final String mVolumeControlId;

        PlaybackInfo(int playbackType, int volumeControl, int maxVolume, int currentVolume,
                AudioAttributes audioAttrs, String volumeControlId) {
            mVolumeType = playbackType;
            mVolumeControl = volumeControl;
            mMaxVolume = maxVolume;
            mCurrentVolume = currentVolume;
            mAudioAttrs = audioAttrs;
            mVolumeControlId = volumeControlId;
        }

        private PlaybackInfo(Parcel in) {
            mVolumeType = in.readInt();
            mVolumeControl = in.readInt();
            mMaxVolume = in.readInt();
            mCurrentVolume = in.readInt();
            if (in.readInt() != 0) {
                int usage = in.readInt();
                int content = in.readInt();
                int flags = in.readInt();
                mAudioAttrs = new AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(content)
                        .setFlags(flags)
                        .build();
            } else {
                mAudioAttrs = null;
            }
            mVolumeControlId = in.readString();
        }

        public int getPlaybackType() {
            return mVolumeType;
        }

        public int getVolumeControl() {
            return mVolumeControl;
        }

        public int getMaxVolume() {
            return mMaxVolume;
        }

        public int getCurrentVolume() {
            return mCurrentVolume;
        }

        public AudioAttributes getAudioAttributes() {
            return mAudioAttrs;
        }

        public String getVolumeControlId() {
            return mVolumeControlId;
        }

        public String toString() {
            return "volumeType=" + mVolumeType
                    + ", volumeControl=" + mVolumeControl
                    + ", maxVolume=" + mMaxVolume
                    + ", currentVolume=" + mCurrentVolume
                    + ", audioAttrs=" + mAudioAttrs
                    + ", volumeControlId=" + mVolumeControlId;
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mVolumeType);
            dest.writeInt(mVolumeControl);
            dest.writeInt(mMaxVolume);
            dest.writeInt(mCurrentVolume);
            if (mAudioAttrs != null) {
                dest.writeInt(1);
                dest.writeInt(mAudioAttrs.getUsage());
                dest.writeInt(mAudioAttrs.getContentType());
                dest.writeInt(mAudioAttrs.getFlags());
            } else {
                dest.writeInt(0);
            }
            dest.writeString(mVolumeControlId);
        }

        public static final Parcelable.Creator<PlaybackInfo> CREATOR =
                new Parcelable.Creator<PlaybackInfo>() {
                    public PlaybackInfo createFromParcel(Parcel in) {
                        return new PlaybackInfo(in);
                    }

                    public PlaybackInfo[] newArray(int size) {
                        return new PlaybackInfo[size];
                    }
                };
    }

    public final class TransportControls {
        TransportControls() {
        }

        public void prepareFromUri(final Uri value, Bundle extras) {
            final Bundle copy = extras == null ? null : new Bundle(extras);
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onPrepareFromUri(value, copy);
                }
            });
        }

        public void prepareFromSearch(final String value, Bundle extras) {
            final Bundle copy = extras == null ? null : new Bundle(extras);
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onPrepareFromSearch(value, copy);
                }
            });
        }

        public void prepareFromMediaId(final String value, Bundle extras) {
            final Bundle copy = extras == null ? null : new Bundle(extras);
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onPrepareFromMediaId(value, copy);
                }
            });
        }

        public void play() {
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onPlay();
                }
            });
        }

        public void pause() {
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onPause();
                }
            });
        }

        public void stop() {
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onStop();
                }
            });
        }

        public void skipToNext() {
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onSkipToNext();
                }
            });
        }

        public void skipToPrevious() {
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onSkipToPrevious();
                }
            });
        }

        public void fastForward() {
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onFastForward();
                }
            });
        }

        public void rewind() {
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onRewind();
                }
            });
        }

        public void prepare() {
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onPrepare();
                }
            });
        }

        public void setRating(Rating rating) {
            Objects.requireNonNull(rating, "rating");
        }

        public void setPlaybackSpeed(final float speed) {
            if (speed == 0 || Float.isNaN(speed) || Float.isInfinite(speed)) {
                throw new IllegalArgumentException("speed");
            }
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onSetPlaybackSpeed(speed);
                }
            });
        }

        public void seekTo(final long position) {
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onSeekTo(position);
                }
            });
        }

        public void skipToQueueItem(final long id) {
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onSkipToQueueItem(id);
                }
            });
        }

        public void playFromMediaId(final String value, Bundle extras) {
            final Bundle copy = extras == null ? null : new Bundle(extras);
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onPlayFromMediaId(value, copy);
                }
            });
        }

        public void playFromSearch(final String value, Bundle extras) {
            final Bundle copy = extras == null ? null : new Bundle(extras);
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onPlayFromSearch(value, copy);
                }
            });
        }

        public void playFromUri(final Uri value, Bundle extras) {
            final Bundle copy = extras == null ? null : new Bundle(extras);
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onPlayFromUri(value, copy);
                }
            });
        }

        public void sendCustomAction(PlaybackState.CustomAction customAction, Bundle args) {
            if (customAction == null) {
                throw new IllegalArgumentException("CustomAction cannot be null.");
            }
            sendCustomAction(customAction.getAction(), args);
        }

        public void sendCustomAction(final String value, Bundle extras) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("CustomAction must specify a non-empty action.");
            }
            final Bundle copy = extras == null ? null : new Bundle(extras);
            session.dispatch(new MediaSession.Action() {
                public void accept(MediaSession.Callback c) {
                    c.onCustomAction(value, copy);
                }
            });
        }
    }
}
