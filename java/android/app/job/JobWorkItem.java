// SPDX-License-Identifier: GPL-3.0-only
package android.app.job;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;

/** API 35 job work value. Delivery state is data, never scheduling authority. */
public final class JobWorkItem implements Parcelable {
    private final Intent intent;
    private final long networkDownloadBytes;
    private final long networkUploadBytes;
    private final long minimumChunkBytes;
    private final PersistableBundle extras;
    private int deliveryCount;
    private int workId;

    public JobWorkItem(Intent intent) {
        this(intent, JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN, null);
    }

    public JobWorkItem(Intent intent, long downloadBytes, long uploadBytes) {
        this(intent, downloadBytes, uploadBytes, JobInfo.NETWORK_BYTES_UNKNOWN, null);
    }

    public JobWorkItem(Intent intent, long downloadBytes, long uploadBytes, long minimumChunkBytes) {
        this(intent, downloadBytes, uploadBytes, minimumChunkBytes, null);
    }

    private JobWorkItem(
            Intent intent,
            long downloadBytes,
            long uploadBytes,
            long minimumChunkBytes,
            PersistableBundle extras) {
        this.intent = intent;
        this.networkDownloadBytes = downloadBytes;
        this.networkUploadBytes = uploadBytes;
        this.minimumChunkBytes = minimumChunkBytes;
        this.extras = extras != null ? extras : PersistableBundle.EMPTY;
    }

    public PersistableBundle getExtras() {
        return extras;
    }

    public Intent getIntent() {
        return intent;
    }

    public long getEstimatedNetworkDownloadBytes() {
        return networkDownloadBytes;
    }

    public long getEstimatedNetworkUploadBytes() {
        return networkUploadBytes;
    }

    public long getMinimumNetworkChunkBytes() {
        return minimumChunkBytes;
    }

    public int getDeliveryCount() {
        return deliveryCount;
    }

    public int getWorkId() {
        return workId;
    }

    public void setWorkId(int id) {
        if (id < 0) throw new IllegalArgumentException("Negative job work ID");
        workId = id;
    }

    public void bumpDeliveryCount() {
        if (deliveryCount == Integer.MAX_VALUE) {
            throw new IllegalStateException("Job delivery count exhausted");
        }
        deliveryCount++;
    }

    @Override
    public String toString() {
        return "JobWorkItem{id=" + workId + " intent=" + intent + " dcount=" + deliveryCount + "}";
    }

    @Override
    public int describeContents() {
        return intent == null ? 0 : intent.describeContents();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(intent == null ? 0 : 1);
        if (intent != null) {
            intent.writeToParcel(out, flags);
        }
        out.writeInt(deliveryCount);
        out.writeInt(workId);
        out.writeLong(networkDownloadBytes);
        out.writeLong(networkUploadBytes);
        out.writeLong(minimumChunkBytes);
        out.writeTypedObject(extras == PersistableBundle.EMPTY ? null : extras, flags);
    }

    private JobWorkItem(Parcel in) {
        int present = in.readInt();
        if (present != 0 && present != 1) {
            throw new IllegalArgumentException("Invalid job intent presence");
        }
        intent = present == 0 ? null : Intent.CREATOR.createFromParcel(in);
        deliveryCount = in.readInt();
        workId = in.readInt();
        if (deliveryCount < 0 || workId < 0) {
            throw new IllegalArgumentException("Invalid job work delivery state");
        }
        if (in.dataAvail() > 0) {
            networkDownloadBytes = in.readLong();
            networkUploadBytes = in.readLong();
            minimumChunkBytes = in.readLong();
            PersistableBundle b = in.readTypedObject(PersistableBundle.CREATOR);
            extras = b != null ? b : PersistableBundle.EMPTY;
        } else {
            networkDownloadBytes = JobInfo.NETWORK_BYTES_UNKNOWN;
            networkUploadBytes = JobInfo.NETWORK_BYTES_UNKNOWN;
            minimumChunkBytes = JobInfo.NETWORK_BYTES_UNKNOWN;
            extras = PersistableBundle.EMPTY;
        }
    }

    public static final Parcelable.Creator<JobWorkItem> CREATOR = new Parcelable.Creator<JobWorkItem>() {
        @Override
        public JobWorkItem createFromParcel(Parcel in) {
            return new JobWorkItem(in);
        }

        @Override
        public JobWorkItem[] newArray(int size) {
            return new JobWorkItem[size];
        }
    };

    public static final class Builder {
        private Intent mIntent;
        private PersistableBundle mExtras = PersistableBundle.EMPTY;
        private long mNetworkDownloadBytes = JobInfo.NETWORK_BYTES_UNKNOWN;
        private long mNetworkUploadBytes = JobInfo.NETWORK_BYTES_UNKNOWN;
        private long mMinimumNetworkChunkBytes = JobInfo.NETWORK_BYTES_UNKNOWN;

        public Builder() {
        }

        public Builder setExtras(PersistableBundle extras) {
            if (extras == null) {
                throw new IllegalArgumentException("extras cannot be null");
            }
            mExtras = extras;
            return this;
        }

        public Builder setIntent(Intent intent) {
            mIntent = intent;
            return this;
        }

        public Builder setEstimatedNetworkBytes(long downloadBytes, long uploadBytes) {
            if (downloadBytes != JobInfo.NETWORK_BYTES_UNKNOWN && downloadBytes < 0) {
                throw new IllegalArgumentException("Invalid network download bytes: " + downloadBytes);
            }
            if (uploadBytes != JobInfo.NETWORK_BYTES_UNKNOWN && uploadBytes < 0) {
                throw new IllegalArgumentException("Invalid network upload bytes: " + uploadBytes);
            }
            mNetworkDownloadBytes = downloadBytes;
            mNetworkUploadBytes = uploadBytes;
            return this;
        }

        public Builder setMinimumNetworkChunkBytes(long chunkSizeBytes) {
            if (chunkSizeBytes != JobInfo.NETWORK_BYTES_UNKNOWN && chunkSizeBytes <= 0) {
                throw new IllegalArgumentException("Minimum chunk size must be positive");
            }
            mMinimumNetworkChunkBytes = chunkSizeBytes;
            return this;
        }

        public JobWorkItem build() {
            return new JobWorkItem(
                    mIntent,
                    mNetworkDownloadBytes,
                    mNetworkUploadBytes,
                    mMinimumNetworkChunkBytes,
                    mExtras);
        }
    }
}
