// SPDX-License-Identifier: GPL-3.0-only
package android.speech;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public class SpeechRecognizer {
    public static final String RESULTS_RECOGNITION = "results_recognition";
    public static final String CONFIDENCE_SCORES = "confidence_scores";
    public static final String RESULTS_ALTERNATIVES = "results_alternatives";
    public static final String RECOGNITION_PARTS = "recognition_parts";

    public static final String DETECTED_LANGUAGE = "detected_language";
    public static final String LANGUAGE_DETECTION_CONFIDENCE_LEVEL =
            "language_detection_confidence_level";
    public static final int LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN = 0;
    public static final int LANGUAGE_DETECTION_CONFIDENCE_LEVEL_NOT_CONFIDENT = 1;
    public static final int LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT = 2;
    public static final int LANGUAGE_DETECTION_CONFIDENCE_LEVEL_HIGHLY_CONFIDENT = 3;

    public static final String TOP_LOCALE_ALTERNATIVES = "top_locale_alternatives";

    public static final String LANGUAGE_SWITCH_RESULT = "language_switch_result";
    public static final int LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED = 0;
    public static final int LANGUAGE_SWITCH_RESULT_SUCCEEDED = 1;
    public static final int LANGUAGE_SWITCH_RESULT_FAILED = 2;
    public static final int LANGUAGE_SWITCH_RESULT_SKIPPED_NO_MODEL = 3;

    public static final int ERROR_NETWORK_TIMEOUT = 1;
    public static final int ERROR_NETWORK = 2;
    public static final int ERROR_AUDIO = 3;
    public static final int ERROR_SERVER = 4;
    public static final int ERROR_CLIENT = 5;
    public static final int ERROR_SPEECH_TIMEOUT = 6;
    public static final int ERROR_NO_MATCH = 7;
    public static final int ERROR_RECOGNIZER_BUSY = 8;
    public static final int ERROR_INSUFFICIENT_PERMISSIONS = 9;
    public static final int ERROR_TOO_MANY_REQUESTS = 10;
    public static final int ERROR_SERVER_DISCONNECTED = 11;
    public static final int ERROR_LANGUAGE_NOT_SUPPORTED = 12;
    public static final int ERROR_LANGUAGE_UNAVAILABLE = 13;
    public static final int ERROR_CANNOT_CHECK_SUPPORT = 14;
    public static final int ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15;

    private final Context mContext;
    private final ComponentName mServiceComponent;
    private final boolean mOnDevice;
    private RecognitionListener mListener;
    private boolean mListening;
    private boolean mDestroyed;

    private SpeechRecognizer(Context context, ComponentName serviceComponent, boolean onDevice) {
        mContext = context;
        mServiceComponent = serviceComponent;
        mOnDevice = onDevice;
    }

    public static boolean isRecognitionAvailable(Context context) {
        if (context == null) {
            return false;
        }
        try {
            PackageManager pm = context.getPackageManager();
            if (pm == null) {
                return false;
            }
            List<ResolveInfo> list = pm.queryIntentServices(
                    new Intent(RecognitionService.SERVICE_INTERFACE), 0);
            return list != null && !list.isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean isOnDeviceRecognitionAvailable(Context context) {
        return false;
    }

    public static SpeechRecognizer createSpeechRecognizer(Context context) {
        return createSpeechRecognizer(context, null);
    }

    public static SpeechRecognizer createSpeechRecognizer(
            Context context, ComponentName serviceComponent) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        return new SpeechRecognizer(context, serviceComponent, false);
    }

    public static SpeechRecognizer createOnDeviceSpeechRecognizer(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        return new SpeechRecognizer(context, null, true);
    }

    public void setRecognitionListener(RecognitionListener listener) {
        if (mDestroyed) {
            return;
        }
        mListener = listener;
    }

    public void startListening(Intent recognizerIntent) {
        if (recognizerIntent == null) {
            throw new IllegalArgumentException("intent must not be null");
        }
        if (mDestroyed) {
            dispatchError(ERROR_CLIENT);
            return;
        }
        if (mListening) {
            dispatchError(ERROR_RECOGNIZER_BUSY);
            return;
        }
        if (mOnDevice ? !isOnDeviceRecognitionAvailable(mContext)
                : (mServiceComponent == null && !isRecognitionAvailable(mContext))) {
            dispatchError(ERROR_CLIENT);
            return;
        }
        mListening = true;
        if (!isRecognitionAvailable(mContext)) {
            mListening = false;
            dispatchError(ERROR_CLIENT);
        }
    }

    public void stopListening() {
        if (mDestroyed) {
            dispatchError(ERROR_CLIENT);
            return;
        }
        if (!mListening) {
            dispatchError(ERROR_CLIENT);
            return;
        }
        mListening = false;
    }

    public void cancel() {
        if (mDestroyed) {
            return;
        }
        mListening = false;
    }

    public void checkRecognitionSupport(
            Intent recognizerIntent,
            Executor executor,
            final RecognitionSupportCallback supportListener) {
        Objects.requireNonNull(recognizerIntent, "intent must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(supportListener, "listener must not be null");
        final int errorCode = mDestroyed ? ERROR_CLIENT : ERROR_CANNOT_CHECK_SUPPORT;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                supportListener.onError(errorCode);
            }
        });
    }

    public void triggerModelDownload(Intent recognizerIntent) {
        Objects.requireNonNull(recognizerIntent, "intent must not be null");
    }

    public void triggerModelDownload(
            Intent recognizerIntent,
            Executor executor,
            final ModelDownloadListener listener) {
        Objects.requireNonNull(recognizerIntent, "intent must not be null");
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        final int errorCode = mDestroyed ? ERROR_CLIENT : ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                listener.onError(errorCode);
            }
        });
    }

    public void destroy() {
        mDestroyed = true;
        mListening = false;
        mListener = null;
    }

    private void dispatchError(final int errorCode) {
        final RecognitionListener listener = mListener;
        if (listener == null) {
            return;
        }
        Looper looper = Looper.myLooper();
        if (looper == null) {
            try {
                looper = Looper.getMainLooper();
            } catch (RuntimeException ignored) {
                looper = null;
            }
        }
        if (looper != null && Looper.myLooper() != looper) {
            new Handler(looper).post(new Runnable() {
                @Override
                public void run() {
                    listener.onError(errorCode);
                }
            });
        } else {
            listener.onError(errorCode);
        }
    }
}
