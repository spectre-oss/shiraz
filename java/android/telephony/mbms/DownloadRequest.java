// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.mbms;

import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

public final class DownloadRequest implements Parcelable {
    public static final int MAX_APP_INTENT_SIZE = 50000;
    public static final int MAX_DESTINATION_URI_SIZE = 50000;

    private final String fileServiceId;
    private final Uri sourceUri;
    private final Uri destinationUri;
    private final int subscriptionId;
    private final String serializedResultIntentForApp;
    private final int version;

    private DownloadRequest(
            String fileServiceId,
            Uri source,
            Uri dest,
            int sub,
            String appIntent,
            int version) {
        this.fileServiceId = fileServiceId;
        this.sourceUri = source;
        this.destinationUri = dest;
        this.subscriptionId = sub;
        this.serializedResultIntentForApp = appIntent;
        this.version = version;
    }

    private DownloadRequest(Parcel in) {
        fileServiceId = in.readString();
        String src = in.readString();
        sourceUri = src != null ? Uri.parse(src) : null;
        String dst = in.readString();
        destinationUri = dst != null ? Uri.parse(dst) : null;
        subscriptionId = in.readInt();
        serializedResultIntentForApp = in.readString();
        version = in.readInt();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(fileServiceId);
        out.writeString(sourceUri != null ? sourceUri.toString() : null);
        out.writeString(destinationUri != null ? destinationUri.toString() : null);
        out.writeInt(subscriptionId);
        out.writeString(serializedResultIntentForApp);
        out.writeInt(version);
    }

    public String getFileServiceId() {
        return fileServiceId;
    }

    public Uri getSourceUri() {
        return sourceUri;
    }

    public Uri getDestinationUri() {
        return destinationUri;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public byte[] toByteArray() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(version);
            dos.writeUTF(fileServiceId != null ? fileServiceId : "");
            dos.writeUTF(sourceUri != null ? sourceUri.toString() : "");
            dos.writeUTF(destinationUri != null ? destinationUri.toString() : "");
            dos.writeInt(subscriptionId);
            dos.writeUTF(serializedResultIntentForApp != null ? serializedResultIntentForApp : "");
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public static int getMaxAppIntentSize() {
        return MAX_APP_INTENT_SIZE;
    }

    public static int getMaxDestinationUriSize() {
        return MAX_DESTINATION_URI_SIZE;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DownloadRequest)) return false;
        DownloadRequest request = (DownloadRequest) o;
        return subscriptionId == request.subscriptionId
                && version == request.version
                && Objects.equals(fileServiceId, request.fileServiceId)
                && Objects.equals(sourceUri, request.sourceUri)
                && Objects.equals(destinationUri, request.destinationUri)
                && Objects.equals(serializedResultIntentForApp, request.serializedResultIntentForApp);
    }

    public int hashCode() {
        return Objects.hash(
                fileServiceId,
                sourceUri,
                destinationUri,
                Integer.valueOf(subscriptionId),
                serializedResultIntentForApp,
                Integer.valueOf(version));
    }

    public static class Builder {
        private String id;
        private Uri source;
        private Uri dest;
        private int subscriptionId;
        private String appIntent;
        private int version = 1;

        public Builder(Uri sourceUri, Uri destinationUri) {
            if (sourceUri == null || destinationUri == null) {
                throw new IllegalArgumentException("Source and destination URIs must be non-null.");
            }
            if (destinationUri.toString().length() > MAX_DESTINATION_URI_SIZE) {
                throw new IllegalArgumentException("Destination URI must not exceed " + MAX_DESTINATION_URI_SIZE);
            }
            source = sourceUri;
            dest = destinationUri;
        }

        public static Builder fromDownloadRequest(DownloadRequest other) {
            Builder result = new Builder(other.sourceUri, other.destinationUri)
                    .setSubscriptionId(other.subscriptionId);
            result.id = other.fileServiceId;
            result.appIntent = other.serializedResultIntentForApp;
            result.version = other.version;
            return result;
        }

        public static Builder fromSerializedRequest(byte[] data) {
            if (data == null) {
                throw new IllegalArgumentException("Serialized data cannot be null");
            }
            try {
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                int ver = dis.readInt();
                String serviceId = dis.readUTF();
                String srcStr = dis.readUTF();
                String dstStr = dis.readUTF();
                int sub = dis.readInt();
                String intentStr = dis.readUTF();
                Builder b = new Builder(Uri.parse(srcStr), Uri.parse(dstStr));
                b.version = ver;
                b.id = serviceId.isEmpty() ? null : serviceId;
                b.subscriptionId = sub;
                b.appIntent = intentStr.isEmpty() ? null : intentStr;
                return b;
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not parse serialized DownloadRequest", e);
            }
        }

        public Builder setServiceInfo(FileServiceInfo serviceInfo) {
            id = serviceInfo != null ? serviceInfo.getServiceId() : null;
            return this;
        }

        public Builder setSubscriptionId(int subscriptionId) {
            this.subscriptionId = subscriptionId;
            return this;
        }

        public Builder setAppIntent(Intent intent) {
            if (intent == null) {
                this.appIntent = null;
            } else {
                StringBuilder sb = new StringBuilder("#Intent;");
                if (intent.getAction() != null) {
                    sb.append("action=").append(intent.getAction()).append(';');
                }
                if (intent.getDataString() != null) {
                    sb.append("data=").append(intent.getDataString()).append(';');
                }
                if (intent.getPackage() != null) {
                    sb.append("package=").append(intent.getPackage()).append(';');
                }
                if (intent.getComponent() != null) {
                    sb.append("component=").append(intent.getComponent().flattenToShortString())
                            .append(';');
                }
                sb.append("end");
                this.appIntent = sb.toString();
            }
            if (this.appIntent != null && this.appIntent.length() > MAX_APP_INTENT_SIZE) {
                throw new IllegalArgumentException("App intent must not exceed length " + MAX_APP_INTENT_SIZE);
            }
            return this;
        }

        public DownloadRequest build() {
            return new DownloadRequest(id, source, dest, subscriptionId, appIntent, version);
        }
    }

    public static final Parcelable.Creator<DownloadRequest> CREATOR =
            new Parcelable.Creator<DownloadRequest>() {
                public DownloadRequest createFromParcel(Parcel in) {
                    return new DownloadRequest(in);
                }

                public DownloadRequest[] newArray(int size) {
                    return new DownloadRequest[size];
                }
            };
}
