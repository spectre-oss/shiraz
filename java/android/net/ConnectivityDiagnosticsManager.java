// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Manager for network connectivity diagnostics and data-stall reports. */
public class ConnectivityDiagnosticsManager {
    private final Map<ConnectivityDiagnosticsCallback, Executor> mCallbacks =
            new IdentityHashMap<ConnectivityDiagnosticsCallback, Executor>();

    ConnectivityDiagnosticsManager() {
    }

    public abstract static class ConnectivityDiagnosticsCallback {
        public ConnectivityDiagnosticsCallback() {
        }

        public void onConnectivityReportAvailable(ConnectivityReport report) {
        }

        public void onDataStallSuspected(DataStallReport report) {
        }

        public void onNetworkConnectivityReported(Network network, boolean hasConnectivity) {
        }
    }

    public static final class ConnectivityReport implements Parcelable {
        public static final int NETWORK_VALIDATION_RESULT_INVALID = 0;
        public static final int NETWORK_VALIDATION_RESULT_VALID = 1;
        public static final int NETWORK_VALIDATION_RESULT_PARTIALLY_VALID = 2;
        public static final int NETWORK_VALIDATION_RESULT_SKIPPED = 3;

        public static final int NETWORK_PROBE_DNS = 4;
        public static final int NETWORK_PROBE_HTTP = 8;
        public static final int NETWORK_PROBE_HTTPS = 16;
        public static final int NETWORK_PROBE_FALLBACK = 32;
        public static final int NETWORK_PROBE_PRIVATE_DNS = 64;

        public static final String KEY_NETWORK_VALIDATION_RESULT = "networkValidationResult";
        public static final String KEY_NETWORK_PROBES_SUCCEEDED_BITMASK = "networkProbesSucceeded";
        public static final String KEY_NETWORK_PROBES_ATTEMPTED_BITMASK = "networkProbesAttempted";

        private final Network mNetwork;
        private final long mReportTimestamp;
        private final LinkProperties mLinkProperties;
        private final NetworkCapabilities mNetworkCapabilities;
        private final PersistableBundle mAdditionalInfo;

        public ConnectivityReport(
                Network network,
                long reportTimestamp,
                LinkProperties linkProperties,
                NetworkCapabilities networkCapabilities,
                PersistableBundle additionalInfo) {
            this.mNetwork = Objects.requireNonNull(network, "network");
            this.mReportTimestamp = reportTimestamp;
            this.mLinkProperties =
                    new LinkProperties(Objects.requireNonNull(linkProperties, "linkProperties"));
            this.mNetworkCapabilities =
                    new NetworkCapabilities(
                            Objects.requireNonNull(networkCapabilities, "networkCapabilities"));
            this.mAdditionalInfo =
                    new PersistableBundle(Objects.requireNonNull(additionalInfo, "additionalInfo"));
        }

        public Network getNetwork() {
            return mNetwork;
        }

        public long getReportTimestamp() {
            return mReportTimestamp;
        }

        public LinkProperties getLinkProperties() {
            return new LinkProperties(mLinkProperties);
        }

        public NetworkCapabilities getNetworkCapabilities() {
            return new NetworkCapabilities(mNetworkCapabilities);
        }

        public PersistableBundle getAdditionalInfo() {
            return new PersistableBundle(mAdditionalInfo);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ConnectivityReport)) {
                return false;
            }
            ConnectivityReport that = (ConnectivityReport) o;
            return mReportTimestamp == that.mReportTimestamp
                    && Objects.equals(mNetwork, that.mNetwork)
                    && Objects.equals(mLinkProperties, that.mLinkProperties)
                    && Objects.equals(mNetworkCapabilities, that.mNetworkCapabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mNetwork,
                    Long.valueOf(mReportTimestamp),
                    mLinkProperties,
                    mNetworkCapabilities);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeTypedObject(mNetwork, flags);
            dest.writeLong(mReportTimestamp);
            dest.writeTypedObject(mLinkProperties, flags);
            dest.writeTypedObject(mNetworkCapabilities, flags);
            dest.writeTypedObject(mAdditionalInfo, flags);
        }

        public static final Parcelable.Creator<ConnectivityReport> CREATOR =
                new Parcelable.Creator<ConnectivityReport>() {
                    @Override
                    public ConnectivityReport createFromParcel(Parcel in) {
                        return new ConnectivityReport(
                                in.readTypedObject(Network.CREATOR),
                                in.readLong(),
                                in.readTypedObject(LinkProperties.CREATOR),
                                in.readTypedObject(NetworkCapabilities.CREATOR),
                                in.readTypedObject(PersistableBundle.CREATOR));
                    }

                    @Override
                    public ConnectivityReport[] newArray(int size) {
                        return new ConnectivityReport[size];
                    }
                };
    }

    public static final class DataStallReport implements Parcelable {
        public static final int DETECTION_METHOD_DNS_EVENTS = 1;
        public static final int DETECTION_METHOD_TCP_METRICS = 2;

        public static final String KEY_DNS_CONSECUTIVE_TIMEOUTS = "dnsConsecutiveTimeouts";
        public static final String KEY_TCP_PACKET_FAIL_RATE = "tcpPacketFailRate";
        public static final String KEY_TCP_METRICS_COLLECTION_PERIOD_MILLIS =
                "tcpMetricsCollectionPeriodMillis";

        private final Network mNetwork;
        private final long mReportTimestamp;
        private final int mDetectionMethod;
        private final LinkProperties mLinkProperties;
        private final NetworkCapabilities mNetworkCapabilities;
        private final PersistableBundle mStallDetails;

        public DataStallReport(
                Network network,
                long reportTimestamp,
                int detectionMethod,
                LinkProperties linkProperties,
                NetworkCapabilities networkCapabilities,
                PersistableBundle stallDetails) {
            this.mNetwork = Objects.requireNonNull(network, "network");
            this.mReportTimestamp = reportTimestamp;
            this.mDetectionMethod = detectionMethod;
            this.mLinkProperties =
                    new LinkProperties(Objects.requireNonNull(linkProperties, "linkProperties"));
            this.mNetworkCapabilities =
                    new NetworkCapabilities(
                            Objects.requireNonNull(networkCapabilities, "networkCapabilities"));
            this.mStallDetails =
                    new PersistableBundle(Objects.requireNonNull(stallDetails, "stallDetails"));
        }

        public Network getNetwork() {
            return mNetwork;
        }

        public long getReportTimestamp() {
            return mReportTimestamp;
        }

        public int getDetectionMethod() {
            return mDetectionMethod;
        }

        public LinkProperties getLinkProperties() {
            return new LinkProperties(mLinkProperties);
        }

        public NetworkCapabilities getNetworkCapabilities() {
            return new NetworkCapabilities(mNetworkCapabilities);
        }

        public PersistableBundle getStallDetails() {
            return new PersistableBundle(mStallDetails);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DataStallReport)) {
                return false;
            }
            DataStallReport that = (DataStallReport) o;
            return mReportTimestamp == that.mReportTimestamp
                    && mDetectionMethod == that.mDetectionMethod
                    && Objects.equals(mNetwork, that.mNetwork)
                    && Objects.equals(mLinkProperties, that.mLinkProperties)
                    && Objects.equals(mNetworkCapabilities, that.mNetworkCapabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mNetwork,
                    Long.valueOf(mReportTimestamp),
                    Integer.valueOf(mDetectionMethod),
                    mLinkProperties,
                    mNetworkCapabilities);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeTypedObject(mNetwork, flags);
            dest.writeLong(mReportTimestamp);
            dest.writeInt(mDetectionMethod);
            dest.writeTypedObject(mLinkProperties, flags);
            dest.writeTypedObject(mNetworkCapabilities, flags);
            dest.writeTypedObject(mStallDetails, flags);
        }

        public static final Parcelable.Creator<DataStallReport> CREATOR =
                new Parcelable.Creator<DataStallReport>() {
                    @Override
                    public DataStallReport createFromParcel(Parcel in) {
                        return new DataStallReport(
                                in.readTypedObject(Network.CREATOR),
                                in.readLong(),
                                in.readInt(),
                                in.readTypedObject(LinkProperties.CREATOR),
                                in.readTypedObject(NetworkCapabilities.CREATOR),
                                in.readTypedObject(PersistableBundle.CREATOR));
                    }

                    @Override
                    public DataStallReport[] newArray(int size) {
                        return new DataStallReport[size];
                    }
                };
    }

    public void registerConnectivityDiagnosticsCallback(
            NetworkRequest request,
            Executor e,
            ConnectivityDiagnosticsCallback callback) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(e, "executor");
        Objects.requireNonNull(callback, "callback");
        synchronized (mCallbacks) {
            if (mCallbacks.containsKey(callback)) {
                throw new IllegalArgumentException("Callback is already registered");
            }
            mCallbacks.put(callback, e);
        }
    }

    public void unregisterConnectivityDiagnosticsCallback(
            ConnectivityDiagnosticsCallback callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }
}
