// SPDX-License-Identifier: GPL-3.0-only
package android.speech;

import android.app.Service;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextParams;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class RecognitionService extends Service {
    public static final String SERVICE_INTERFACE = "android.speech.RecognitionService";
    public static final String SERVICE_META_DATA = "android.speech";

    private final Map<IBinder, Callback> mSessions = new HashMap<IBinder, Callback>();
    private RecognitionServiceBinder mBinder = new RecognitionServiceBinder(this);

    public RecognitionService() {
    }

    protected abstract void onStartListening(Intent recognizerIntent, Callback listener);

    protected abstract void onCancel(Callback listener);

    protected abstract void onStopListening(Callback listener);

    public void onCheckRecognitionSupport(
            Intent recognizerIntent, SupportCallback supportCallback) {
        supportCallback.onError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT);
    }

    public void onCheckRecognitionSupport(
            Intent recognizerIntent,
            AttributionSource attributionSource,
            SupportCallback supportCallback) {
        onCheckRecognitionSupport(recognizerIntent, supportCallback);
    }

    public void onTriggerModelDownload(Intent recognizerIntent) {
    }

    public void onTriggerModelDownload(
            Intent recognizerIntent, AttributionSource attributionSource) {
        onTriggerModelDownload(recognizerIntent);
    }

    public void onTriggerModelDownload(
            Intent recognizerIntent,
            AttributionSource attributionSource,
            ModelDownloadListener listener) {
        if (listener != null) {
            listener.onError(SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS);
        }
    }

    public Context createContext(ContextParams contextParams) {
        Objects.requireNonNull(contextParams, "contextParams must not be null");
        return new ContextWrapper(this);
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        for (Callback callback : mSessions.values()) {
            callback.mFinished = true;
        }
        mSessions.clear();
        if (mBinder != null) {
            mBinder.clearReference();
        }
        super.onDestroy();
    }

    public int getMaxConcurrentSessionsCount() {
        return 1;
    }

    void dispatchStartListening(
            Intent intent,
            RecognitionListener listener,
            IBinder key,
            AttributionSource attributionSource) {
        Callback currentCallback = mSessions.get(key);
        if (currentCallback == null) {
            if (mSessions.size() >= getMaxConcurrentSessionsCount()) {
                if (listener != null) {
                    listener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY);
                }
                return;
            }
            AttributionSource source = attributionSource != null
                    ? attributionSource : AttributionSource.myAttributionSource();
            currentCallback = new Callback(listener, source, key);
            mSessions.put(key, currentCallback);
            onStartListening(intent, currentCallback);
        } else {
            if (listener != null) {
                listener.onError(SpeechRecognizer.ERROR_CLIENT);
            }
        }
    }

    void dispatchStopListening(RecognitionListener listener, IBinder key) {
        Callback currentCallback = mSessions.get(key);
        if (currentCallback == null) {
            if (listener != null) {
                listener.onError(SpeechRecognizer.ERROR_CLIENT);
            }
        } else {
            onStopListening(currentCallback);
        }
    }

    void dispatchCancel(IBinder key) {
        Callback currentCallback = mSessions.remove(key);
        if (currentCallback != null) {
            currentCallback.mFinished = true;
            onCancel(currentCallback);
        }
    }

    public class Callback {
        private final RecognitionListener mListener;
        private final AttributionSource mCallingAttributionSource;
        private final IBinder mKey;
        boolean mFinished;

        Callback(
                RecognitionListener listener,
                AttributionSource attributionSource,
                IBinder key) {
            mListener = listener;
            mCallingAttributionSource = attributionSource != null
                    ? attributionSource : AttributionSource.myAttributionSource();
            mKey = key;
        }

        public void beginningOfSpeech() throws RemoteException {
            if (mListener != null && !mFinished) {
                mListener.onBeginningOfSpeech();
            }
        }

        public void bufferReceived(byte[] buffer) throws RemoteException {
            if (mListener != null && !mFinished) {
                mListener.onBufferReceived(buffer);
            }
        }

        public void endOfSpeech() throws RemoteException {
            if (mListener != null && !mFinished) {
                mListener.onEndOfSpeech();
            }
        }

        public void error(int error) throws RemoteException {
            if (mKey != null) {
                mSessions.remove(mKey);
            }
            if (mListener != null && !mFinished) {
                mFinished = true;
                mListener.onError(error);
            }
        }

        public void partialResults(Bundle partialResults) throws RemoteException {
            if (mListener != null && !mFinished) {
                mListener.onPartialResults(partialResults);
            }
        }

        public void readyForSpeech(Bundle params) throws RemoteException {
            if (mListener != null && !mFinished) {
                mListener.onReadyForSpeech(params);
            }
        }

        public void results(Bundle results) throws RemoteException {
            if (mKey != null) {
                mSessions.remove(mKey);
            }
            if (mListener != null && !mFinished) {
                mFinished = true;
                mListener.onResults(results);
            }
        }

        public void rmsChanged(float rmsdB) throws RemoteException {
            if (mListener != null && !mFinished) {
                mListener.onRmsChanged(rmsdB);
            }
        }

        public void segmentResults(Bundle results) throws RemoteException {
            if (mListener != null && !mFinished) {
                mListener.onSegmentResults(results);
            }
        }

        public void endOfSegmentedSession() throws RemoteException {
            if (mKey != null) {
                mSessions.remove(mKey);
            }
            if (mListener != null && !mFinished) {
                mFinished = true;
                mListener.onEndOfSegmentedSession();
            }
        }

        public void languageDetection(Bundle results) {
            if (mListener != null && !mFinished) {
                mListener.onLanguageDetection(results);
            }
        }

        public int getCallingUid() {
            return mCallingAttributionSource.getUid();
        }

        public AttributionSource getCallingAttributionSource() {
            return mCallingAttributionSource;
        }
    }

    public static class SupportCallback {
        private final RecognitionSupportCallback mCallback;

        SupportCallback(RecognitionSupportCallback callback) {
            mCallback = callback;
        }

        public void onSupportResult(RecognitionSupport recognitionSupport) {
            if (mCallback != null) {
                mCallback.onSupportResult(recognitionSupport);
            }
        }

        public void onError(int error) {
            if (mCallback != null) {
                mCallback.onError(error);
            }
        }
    }

    private static final class RecognitionServiceBinder extends Binder {
        private RecognitionService mServiceRef;

        RecognitionServiceBinder(RecognitionService service) {
            mServiceRef = service;
        }

        RecognitionService getService() {
            return mServiceRef;
        }

        void clearReference() {
            mServiceRef = null;
        }
    }
}
