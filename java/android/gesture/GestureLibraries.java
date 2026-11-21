// SPDX-License-Identifier: GPL-3.0-only
package android.gesture;

import android.content.Context;
import android.content.res.Resources;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

public final class GestureLibraries {
    private GestureLibraries() {
    }

    public static GestureLibrary fromFile(String path) {
        return fromFile(new File(path));
    }

    public static GestureLibrary fromFile(File path) {
        return new FileGestureLibrary(path);
    }

    public static GestureLibrary fromFileDescriptor(ParcelFileDescriptor pfd) {
        return new FileGestureLibrary(pfd.getFileDescriptor());
    }

    public static GestureLibrary fromPrivateFile(Context context, String name) {
        return fromFile(context.getFileStreamPath(name));
    }

    public static GestureLibrary fromRawResource(Context context, int resourceId) {
        return new ResourceGestureLibrary(context, resourceId);
    }

    private static class FileGestureLibrary extends GestureLibrary {
        private final File mPath;
        private final FileDescriptor mFd;

        FileGestureLibrary(File path) {
            mPath = path;
            mFd = null;
        }

        FileGestureLibrary(FileDescriptor fd) {
            mPath = null;
            mFd = fd;
        }

        @Override
        public boolean isReadOnly() {
            if (mPath != null) {
                if (mPath.exists()) {
                    return !mPath.canWrite();
                }
                File parent = mPath.getParentFile();
                return parent != null && parent.exists() && !parent.canWrite();
            }
            return false;
        }

        @Override
        public boolean save() {
            if (!mStore.hasChanged()) {
                return true;
            }
            if (mPath != null) {
                final File file = mPath;
                final File parentFile = file.getParentFile();
                if (parentFile != null && !parentFile.exists()) {
                    if (!parentFile.mkdirs()) {
                        return false;
                    }
                }
                try {
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    mStore.save(new FileOutputStream(file), true);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            } else if (mFd != null) {
                try {
                    mStore.save(new FileOutputStream(mFd), false);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }
            return false;
        }

        @Override
        public boolean load() {
            if (mPath != null) {
                final File file = mPath;
                if (file.exists() && file.canRead()) {
                    try {
                        mStore.load(new FileInputStream(file), true);
                        return true;
                    } catch (IOException e) {
                        return false;
                    }
                }
                return false;
            } else if (mFd != null) {
                try {
                    mStore.load(new FileInputStream(mFd), false);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }
            return false;
        }
    }

    private static class ResourceGestureLibrary extends GestureLibrary {
        private final WeakReference<Context> mContext;
        private final int mResourceId;

        ResourceGestureLibrary(Context context, int resourceId) {
            mContext = new WeakReference<Context>(context);
            mResourceId = resourceId;
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }

        @Override
        public boolean save() {
            return false;
        }

        @Override
        public boolean load() {
            final Context context = mContext.get();
            if (context != null) {
                final Resources res = context.getResources();
                if (res == null) {
                    return false;
                }
                try {
                    InputStream in = res.openRawResource(mResourceId);
                    mStore.load(in, true);
                    return true;
                } catch (Resources.NotFoundException e) {
                    return false;
                } catch (IOException e) {
                    return false;
                }
            }
            return false;
        }
    }
}
