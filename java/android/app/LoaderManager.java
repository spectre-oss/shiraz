// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Loader;
import android.os.Bundle;
import android.util.SparseArray;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Objects;

@Deprecated
public abstract class LoaderManager {
    private static volatile boolean sDebug;

    @Deprecated
    public interface LoaderCallbacks<D> {
        Loader<D> onCreateLoader(int id, Bundle args);

        void onLoadFinished(Loader<D> loader, D data);

        void onLoaderReset(Loader<D> loader);
    }

    public LoaderManager() {
    }

    public abstract <D> Loader<D> initLoader(int id, Bundle args, LoaderCallbacks<D> callback);

    public abstract <D> Loader<D> restartLoader(int id, Bundle args, LoaderCallbacks<D> callback);

    public abstract void destroyLoader(int id);

    public abstract <D> Loader<D> getLoader(int id);

    public abstract void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args);

    public static void enableDebugLogging(boolean enabled) {
        sDebug = enabled;
    }

    static boolean isDebugEnabled() {
        return sDebug;
    }

    static final class Impl extends LoaderManager {
        private static final class Entry<D> implements Loader.OnLoadCompleteListener<D>,
                Loader.OnLoadCanceledListener<D> {
            final int id;
            Bundle args;
            LoaderCallbacks<D> callbacks;
            Loader<D> loader;
            boolean haveData;
            D data;
            boolean started;
            boolean destroyed;

            Entry(int id, Bundle args, LoaderCallbacks<D> callbacks) {
                this.id = id;
                this.args = args;
                this.callbacks = callbacks;
            }

            void start() {
                if (started || destroyed) {
                    return;
                }
                started = true;
                if (loader == null && callbacks != null) {
                    loader = callbacks.onCreateLoader(id, args);
                }
                if (loader != null) {
                    loader.registerListener(id, this);
                    loader.registerOnLoadCanceledListener(this);
                    loader.startLoading();
                }
            }

            void stop() {
                started = false;
                if (loader != null) {
                    loader.stopLoading();
                }
            }

            void destroy() {
                destroyed = true;
                boolean needReset = haveData;
                haveData = false;
                if (callbacks != null && loader != null && needReset) {
                    callbacks.onLoaderReset(loader);
                }
                callbacks = null;
                data = null;
                if (loader != null) {
                    try {
                        loader.unregisterListener(this);
                    } catch (RuntimeException ignored) {
                    }
                    try {
                        loader.unregisterOnLoadCanceledListener(this);
                    } catch (RuntimeException ignored) {
                    }
                    loader.reset();
                }
            }

            @Override
            public void onLoadComplete(Loader<D> source, D result) {
                if (destroyed) {
                    return;
                }
                haveData = true;
                data = result;
                if (callbacks != null) {
                    callbacks.onLoadFinished(source, result);
                }
            }

            @Override
            public void onLoadCanceled(Loader<D> source) {
            }
        }

        private final SparseArray<Entry<?>> mLoaders = new SparseArray<Entry<?>>();
        private boolean mStarted = true;

        @Override
        @SuppressWarnings("unchecked")
        public <D> Loader<D> initLoader(int id, Bundle args, LoaderCallbacks<D> callback) {
            Objects.requireNonNull(callback, "callback");
            Entry<D> existing = (Entry<D>) mLoaders.get(id);
            if (existing == null) {
                Entry<D> created = new Entry<D>(id, args, callback);
                mLoaders.put(id, created);
                if (mStarted) {
                    created.start();
                }
                return created.loader;
            }
            existing.callbacks = callback;
            if (mStarted && existing.haveData && existing.loader != null) {
                callback.onLoadFinished(existing.loader, existing.data);
            }
            return existing.loader;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <D> Loader<D> restartLoader(int id, Bundle args, LoaderCallbacks<D> callback) {
            Objects.requireNonNull(callback, "callback");
            Entry<D> existing = (Entry<D>) mLoaders.get(id);
            if (existing != null) {
                existing.destroy();
                mLoaders.remove(id);
            }
            Entry<D> created = new Entry<D>(id, args, callback);
            mLoaders.put(id, created);
            if (mStarted) {
                created.start();
            }
            return created.loader;
        }

        @Override
        public void destroyLoader(int id) {
            Entry<?> existing = mLoaders.get(id);
            if (existing != null) {
                mLoaders.remove(id);
                existing.destroy();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <D> Loader<D> getLoader(int id) {
            Entry<D> existing = (Entry<D>) mLoaders.get(id);
            return existing != null ? existing.loader : null;
        }

        void doStart() {
            mStarted = true;
            for (int i = 0; i < mLoaders.size(); i++) {
                mLoaders.valueAt(i).start();
            }
        }

        void doStop(boolean retain) {
            mStarted = false;
            if (!retain) {
                for (int i = 0; i < mLoaders.size(); i++) {
                    mLoaders.valueAt(i).stop();
                }
            }
        }

        void doDestroy() {
            for (int i = mLoaders.size() - 1; i >= 0; i--) {
                mLoaders.valueAt(i).destroy();
            }
            mLoaders.clear();
        }

        @Override
        public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
            if (writer == null) {
                return;
            }
            String p = prefix != null ? prefix : "";
            for (int i = 0; i < mLoaders.size(); i++) {
                Entry<?> entry = mLoaders.valueAt(i);
                writer.print(p);
                writer.print("  #");
                writer.print(mLoaders.keyAt(i));
                writer.print(": ");
                writer.println(entry.loader);
            }
        }
    }
}
