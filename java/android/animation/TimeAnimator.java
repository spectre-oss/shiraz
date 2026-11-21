// SPDX-License-Identifier: GPL-3.0-only
package android.animation;

/** Continuous frame-clock animator notifying a {@link TimeListener} on every frame. */
public class TimeAnimator extends ValueAnimator {
    public interface TimeListener {
        void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime);
    }

    private TimeListener listener;
    private long previousTime = -1;

    public TimeAnimator() {
        setFloatValues(0f, 1f);
        setRepeatCount(INFINITE);
    }

    @Override
    public void start() {
        previousTime = -1;
        super.start();
    }

    @Override
    void shirazOnPlayTime(long currentTime) {
        if (listener != null) {
            long delta = previousTime < 0 ? 0 : currentTime - previousTime;
            previousTime = currentTime;
            listener.onTimeUpdate(this, currentTime, delta);
        }
    }

    @Override
    public void setCurrentPlayTime(long playTime) {
        if (playTime < 0) throw new IllegalArgumentException("Play time");
        shirazCheckThread();
        shirazSetPlayTimeOnly(playTime);
        shirazOnPlayTime(playTime);
    }

    public void setTimeListener(TimeListener listener) {
        this.listener = listener;
    }

    @Override
    public TimeAnimator clone() {
        TimeAnimator copy = new TimeAnimator();
        shirazCopyTo(copy);
        copy.listener = null;
        copy.previousTime = -1;
        return copy;
    }
}
