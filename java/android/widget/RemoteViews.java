// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Outline;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewOutlineProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/** Remote view hierarchy descriptor with Parcelable metadata transport. */
public class RemoteViews implements Parcelable {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface RemoteView {
    }

    public static class ActionException extends RuntimeException {
        public ActionException(Exception ex) {
            super(ex);
        }

        public ActionException(String message) {
            super(message);
        }
    }

    public static class RemoteResponse {
        private PendingIntent mPendingIntent;
        private Intent mFillInIntent;
        private final ArrayList<Integer> mViewIds = new ArrayList<Integer>();
        private final ArrayList<String> mElementNames = new ArrayList<String>();

        public RemoteResponse() {
        }

        public static RemoteResponse fromPendingIntent(PendingIntent pendingIntent) {
            RemoteResponse response = new RemoteResponse();
            response.mPendingIntent = pendingIntent;
            return response;
        }

        public static RemoteResponse fromFillInIntent(Intent fillIntent) {
            RemoteResponse response = new RemoteResponse();
            response.mFillInIntent = fillIntent;
            return response;
        }

        public RemoteResponse addSharedElement(int viewId, String sharedElementName) {
            mViewIds.add(Integer.valueOf(viewId));
            mElementNames.add(sharedElementName);
            return this;
        }
    }

    public static final class RemoteCollectionItems implements Parcelable {
        private final long[] mIds;
        private final RemoteViews[] mViews;
        private final boolean mHasStableIds;
        private final int mViewTypeCount;

        RemoteCollectionItems(long[] ids, RemoteViews[] views, boolean hasStableIds, int viewTypeCount) {
            mIds = ids;
            mViews = views;
            mHasStableIds = hasStableIds;
            mViewTypeCount = Math.max(1, viewTypeCount);
        }

        RemoteCollectionItems(Parcel in) {
            mHasStableIds = in.readInt() != 0;
            mViewTypeCount = in.readInt();
            int count = in.readInt();
            mIds = new long[count];
            mViews = new RemoteViews[count];
            for (int i = 0; i < count; i++) {
                mIds[i] = in.readLong();
                if (in.readInt() != 0) {
                    mViews[i] = RemoteViews.CREATOR.createFromParcel(in);
                }
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mHasStableIds ? 1 : 0);
            dest.writeInt(mViewTypeCount);
            dest.writeInt(mIds.length);
            for (int i = 0; i < mIds.length; i++) {
                dest.writeLong(mIds[i]);
                if (mViews[i] != null) {
                    dest.writeInt(1);
                    mViews[i].writeToParcel(dest, flags);
                } else {
                    dest.writeInt(0);
                }
            }
        }

        public long getItemId(int position) {
            return mIds[position];
        }

        public RemoteViews getItemView(int position) {
            return mViews[position];
        }

        public int getItemCount() {
            return mIds.length;
        }

        public int getViewTypeCount() {
            return mViewTypeCount;
        }

        public boolean hasStableIds() {
            return mHasStableIds;
        }

        public static final Parcelable.Creator<RemoteCollectionItems> CREATOR =
                new Parcelable.Creator<RemoteCollectionItems>() {
                    @Override
                    public RemoteCollectionItems createFromParcel(Parcel source) {
                        return new RemoteCollectionItems(source);
                    }

                    @Override
                    public RemoteCollectionItems[] newArray(int size) {
                        return new RemoteCollectionItems[size];
                    }
                };

        public static final class Builder {
            private final ArrayList<Long> mIds = new ArrayList<Long>();
            private final ArrayList<RemoteViews> mViews = new ArrayList<RemoteViews>();
            private boolean mHasStableIds;
            private int mViewTypeCount;

            public Builder() {
            }

            public Builder addItem(long id, RemoteViews view) {
                if (view == null) {
                    throw new NullPointerException("view");
                }
                mIds.add(Long.valueOf(id));
                mViews.add(view);
                return this;
            }

            public Builder setHasStableIds(boolean hasStableIds) {
                mHasStableIds = hasStableIds;
                return this;
            }

            public Builder setViewTypeCount(int viewTypeCount) {
                mViewTypeCount = viewTypeCount;
                return this;
            }

            public RemoteCollectionItems build() {
                int n = mIds.size();
                long[] ids = new long[n];
                RemoteViews[] views = new RemoteViews[n];
                for (int i = 0; i < n; i++) {
                    ids[i] = mIds.get(i).longValue();
                    views[i] = mViews.get(i);
                }
                int typeCount = mViewTypeCount > 0 ? mViewTypeCount : 1;
                return new RemoteCollectionItems(ids, views, mHasStableIds, typeCount);
            }
        }
    }

    public static final class DrawInstructions {
        private final List<byte[]> mInstructions;

        DrawInstructions(List<byte[]> instructions) {
            mInstructions = instructions;
        }

        public static long getSupportedVersion() {
            return 1L;
        }

        public static final class Builder {
            private final ArrayList<byte[]> mInstructions;

            public Builder(List<byte[]> instructions) {
                mInstructions = instructions != null
                        ? new ArrayList<byte[]>(instructions) : new ArrayList<byte[]>();
            }

            public DrawInstructions build() {
                return new DrawInstructions(mInstructions);
            }
        }
    }

    public static final class RemoteViewOutlineProvider extends ViewOutlineProvider {
        private final float mRadius;

        public RemoteViewOutlineProvider(float radius) {
            mRadius = radius;
        }

        public float getRadius() {
            return mRadius;
        }

        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mRadius);
        }
    }

    private final String mPackage;
    private final int mLayoutId;
    private DrawInstructions mDrawInstructions;

    public RemoteViews(String packageName, int layoutId) {
        mPackage = packageName;
        mLayoutId = layoutId;
    }

    public RemoteViews(DrawInstructions drawInstructions) {
        mPackage = "";
        mLayoutId = 0;
        mDrawInstructions = drawInstructions;
    }

    public RemoteViews(Parcel parcel) {
        mPackage = parcel.readString();
        mLayoutId = parcel.readInt();
    }

    public String getPackage() {
        return mPackage;
    }

    public int getLayoutId() {
        return mLayoutId;
    }

    public void setOnClickResponse(int viewId, RemoteResponse response) {
    }

    public void setOnCheckedChangeResponse(int viewId, RemoteResponse response) {
    }

    public void setRemoteAdapter(int viewId, RemoteCollectionItems items) {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackage);
        dest.writeInt(mLayoutId);
    }

    public static final Parcelable.Creator<RemoteViews> CREATOR =
            new Parcelable.Creator<RemoteViews>() {
                @Override
                public RemoteViews createFromParcel(Parcel parcel) {
                    return new RemoteViews(parcel);
                }

                @Override
                public RemoteViews[] newArray(int size) {
                    return new RemoteViews[size];
                }
            };
}
