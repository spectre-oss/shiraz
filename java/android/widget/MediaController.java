// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.Formatter;
import java.util.Locale;

/** Transport controls overlay (play/pause, rewind, fast-forward, seek bar) for MediaPlayerControl. */
public class MediaController extends FrameLayout {
    public interface MediaPlayerControl {
        void start();

        void pause();

        int getDuration();

        int getCurrentPosition();

        void seekTo(int pos);

        boolean isPlaying();

        int getBufferPercentage();

        boolean canPause();

        boolean canSeekBackward();

        boolean canSeekForward();

        int getAudioSessionId();
    }

    private MediaPlayerControl mPlayer;
    private final Context mContext;
    private View mAnchor;
    private PopupWindow mWindow;
    private SeekBar mProgress;
    private TextView mEndTime;
    private TextView mCurrentTime;
    private boolean mShowing;
    private boolean mDragging;
    private static final int sDefaultTimeout = 3000;
    private final boolean mUseFastForward;
    private boolean mFromXml;
    private View.OnClickListener mNextListener;
    private View.OnClickListener mPrevListener;
    private final StringBuilder mFormatBuilder = new StringBuilder();
    private final Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
    private Button mPauseButton;
    private Button mFfwdButton;
    private Button mRewButton;
    private Button mNextButton;
    private Button mPrevButton;

    private final Runnable mFadeOut = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private final Runnable mShowProgress = new Runnable() {
        @Override
        public void run() {
            int pos = setProgress();
            if (!mDragging && mShowing && mPlayer != null && mPlayer.isPlaying()) {
                postDelayed(mShowProgress, 1000 - (pos % 1000));
            }
        }
    };

    public MediaController(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mUseFastForward = true;
        mFromXml = true;
    }

    public MediaController(Context context, boolean useFastForward) {
        super(context);
        mContext = context;
        mUseFastForward = useFastForward;
        initControllerView();
    }

    public MediaController(Context context) {
        this(context, true);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() == 0) {
            initControllerView();
        }
    }

    private void initControllerView() {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xcc000000);

        LinearLayout buttons = new LinearLayout(mContext);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        mPrevButton = new Button(mContext);
        mPrevButton.setText("|<");
        mPrevButton.setVisibility(GONE);

        mRewButton = new Button(mContext);
        mRewButton.setText("<<");
        mRewButton.setVisibility(mUseFastForward ? VISIBLE : GONE);
        mRewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPlayer == null) {
                    return;
                }
                int pos = mPlayer.getCurrentPosition() - 5000;
                mPlayer.seekTo(Math.max(0, pos));
                setProgress();
                show(sDefaultTimeout);
            }
        });

        mPauseButton = new Button(mContext);
        mPauseButton.setText(">");
        mPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doPauseResume();
                show(sDefaultTimeout);
            }
        });

        mFfwdButton = new Button(mContext);
        mFfwdButton.setText(">>");
        mFfwdButton.setVisibility(mUseFastForward ? VISIBLE : GONE);
        mFfwdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPlayer == null) {
                    return;
                }
                int pos = mPlayer.getCurrentPosition() + 15000;
                mPlayer.seekTo(Math.min(mPlayer.getDuration(), pos));
                setProgress();
                show(sDefaultTimeout);
            }
        });

        mNextButton = new Button(mContext);
        mNextButton.setText(">|");
        mNextButton.setVisibility(GONE);

        buttons.addView(mPrevButton);
        buttons.addView(mRewButton);
        buttons.addView(mPauseButton);
        buttons.addView(mFfwdButton);
        buttons.addView(mNextButton);

        LinearLayout seekRow = new LinearLayout(mContext);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);

        mCurrentTime = new TextView(mContext);
        mCurrentTime.setTextColor(0xffffffff);
        mCurrentTime.setText("00:00");

        mProgress = new SeekBar(mContext);
        mProgress.setMax(1000);
        mProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                show(3600000);
                mDragging = true;
                removeCallbacks(mShowProgress);
            }

            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
                if (!fromuser || mPlayer == null) {
                    return;
                }
                long duration = mPlayer.getDuration();
                long newposition = (duration * progress) / 1000L;
                mPlayer.seekTo((int) newposition);
                if (mCurrentTime != null) {
                    mCurrentTime.setText(stringForTime((int) newposition));
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                mDragging = false;
                setProgress();
                updatePausePlay();
                show(sDefaultTimeout);
                post(mShowProgress);
            }
        });

        mEndTime = new TextView(mContext);
        mEndTime.setTextColor(0xffffffff);
        mEndTime.setText("00:00");

        seekRow.addView(mCurrentTime, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        seekRow.addView(mProgress, new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f));
        seekRow.addView(mEndTime, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        root.addView(buttons, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        root.addView(seekRow, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(root, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setMediaPlayer(MediaPlayerControl player) {
        mPlayer = player;
        updatePausePlay();
    }

    public void setAnchorView(View view) {
        mAnchor = view;
    }

    public void show() {
        show(sDefaultTimeout);
    }

    public void show(int timeout) {
        if (!mShowing && mAnchor != null) {
            setProgress();
            if (mPauseButton != null) {
                mPauseButton.requestFocus();
            }
            disableUnsupportedButtons();
            if (!mFromXml && mAnchor.isAttachedToWindow()) {
                if (mWindow == null) {
                    mWindow = new PopupWindow(
                            this,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            false);
                }
                if (getParent() == null) {
                    mWindow.showAsDropDown(mAnchor);
                }
            }
            mShowing = true;
        }
        updatePausePlay();
        post(mShowProgress);
        removeCallbacks(mFadeOut);
        if (timeout != 0) {
            postDelayed(mFadeOut, timeout);
        }
    }

    public boolean isShowing() {
        return mShowing;
    }

    public void hide() {
        if (mAnchor == null) {
            return;
        }
        if (mShowing) {
            removeCallbacks(mShowProgress);
            removeCallbacks(mFadeOut);
            if (mWindow != null && mWindow.isShowing()) {
                mWindow.dismiss();
            }
            mShowing = false;
        }
    }

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d",
                    Integer.valueOf(hours), Integer.valueOf(minutes), Integer.valueOf(seconds))
                    .toString();
        } else {
            return mFormatter.format("%02d:%02d",
                    Integer.valueOf(minutes), Integer.valueOf(seconds)).toString();
        }
    }

    private int setProgress() {
        if (mPlayer == null || mDragging) {
            return 0;
        }
        int position = mPlayer.getCurrentPosition();
        int duration = mPlayer.getDuration();
        if (mProgress != null) {
            if (duration > 0) {
                long pos = 1000L * position / duration;
                mProgress.setProgress((int) pos);
            }
            int percent = mPlayer.getBufferPercentage();
            mProgress.setSecondaryProgress(percent * 10);
        }
        if (mEndTime != null) {
            mEndTime.setText(stringForTime(duration));
        }
        if (mCurrentTime != null) {
            mCurrentTime.setText(stringForTime(position));
        }
        return position;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                show(0);
                break;
            case MotionEvent.ACTION_UP:
                show(sDefaultTimeout);
                break;
            case MotionEvent.ACTION_CANCEL:
                hide();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        show(sDefaultTimeout);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        final boolean uniqueDown = event.getRepeatCount() == 0
                && event.getAction() == KeyEvent.ACTION_DOWN;
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (uniqueDown) {
                doPauseResume();
                show(sDefaultTimeout);
                if (mPauseButton != null) {
                    mPauseButton.requestFocus();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (uniqueDown && mPlayer != null && !mPlayer.isPlaying()) {
                mPlayer.start();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (uniqueDown && mPlayer != null && mPlayer.isPlaying()) {
                mPlayer.pause();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) {
                hide();
            }
            return true;
        }
        show(sDefaultTimeout);
        return super.dispatchKeyEvent(event);
    }

    private void updatePausePlay() {
        if (mPauseButton == null || mPlayer == null) {
            return;
        }
        mPauseButton.setText(mPlayer.isPlaying() ? "||" : ">");
    }

    private void doPauseResume() {
        if (mPlayer == null) {
            return;
        }
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        } else {
            mPlayer.start();
        }
        updatePausePlay();
    }

    private void disableUnsupportedButtons() {
        if (mPlayer == null) {
            return;
        }
        if (mPauseButton != null && !mPlayer.canPause()) {
            mPauseButton.setEnabled(false);
        }
        if (mRewButton != null && !mPlayer.canSeekBackward()) {
            mRewButton.setEnabled(false);
        }
        if (mFfwdButton != null && !mPlayer.canSeekForward()) {
            mFfwdButton.setEnabled(false);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mPauseButton != null) {
            mPauseButton.setEnabled(enabled);
        }
        if (mFfwdButton != null) {
            mFfwdButton.setEnabled(enabled);
        }
        if (mRewButton != null) {
            mRewButton.setEnabled(enabled);
        }
        if (mNextButton != null) {
            mNextButton.setEnabled(enabled && mNextListener != null);
        }
        if (mPrevButton != null) {
            mPrevButton.setEnabled(enabled && mPrevListener != null);
        }
        if (mProgress != null) {
            mProgress.setEnabled(enabled);
        }
        disableUnsupportedButtons();
        super.setEnabled(enabled);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return MediaController.class.getName();
    }

    public void setPrevNextListeners(View.OnClickListener next, View.OnClickListener prev) {
        mNextListener = next;
        mPrevListener = prev;
        if (mNextButton != null) {
            mNextButton.setOnClickListener(mNextListener);
            mNextButton.setVisibility(mNextListener != null ? VISIBLE : GONE);
        }
        if (mPrevButton != null) {
            mPrevButton.setOnClickListener(mPrevListener);
            mPrevButton.setVisibility(mPrevListener != null ? VISIBLE : GONE);
        }
    }
}
