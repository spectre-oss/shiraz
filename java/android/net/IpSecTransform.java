// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.content.Context;
import android.os.OutcomeReceiver;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Represents an IPsec security association transform. */
public final class IpSecTransform implements AutoCloseable {
    private final InetAddress mSourceAddress;
    private final int mSpi;
    private boolean mClosed;

    IpSecTransform(InetAddress sourceAddress, int spi) {
        this.mSourceAddress = sourceAddress;
        this.mSpi = spi;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IpSecTransform)) {
            return false;
        }
        IpSecTransform that = (IpSecTransform) other;
        return mSpi == that.mSpi && Objects.equals(mSourceAddress, that.mSourceAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSourceAddress, Integer.valueOf(mSpi));
    }

    @Override
    public void close() {
        mClosed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public void requestIpSecTransformState(
            Executor executor, final OutcomeReceiver<IpSecTransformState, RuntimeException> callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        final boolean closed = mClosed;
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        if (closed) {
                            callback.onError(new IllegalStateException("Transform is closed"));
                        } else {
                            callback.onResult(
                                    new IpSecTransformState.Builder()
                                            .setTimestampMillis(System.currentTimeMillis())
                                            .build());
                        }
                    }
                });
    }

    @Override
    public String toString() {
        return "IpSecTransform{src=" + mSourceAddress + ", spi=" + mSpi + "}";
    }

    public static class Builder {
        private final Context mContext;
        private IpSecAlgorithm mEncryption;
        private IpSecAlgorithm mAuthentication;
        private IpSecAlgorithm mAuthenticatedEncryption;
        private IpSecManager.UdpEncapsulationSocket mUdpSocket;
        private int mRemotePort;

        public Builder(Context context) {
            this.mContext = Objects.requireNonNull(context, "context");
        }

        public Builder setEncryption(IpSecAlgorithm algo) {
            this.mEncryption = Objects.requireNonNull(algo, "algo");
            return this;
        }

        public Builder setAuthentication(IpSecAlgorithm algo) {
            this.mAuthentication = Objects.requireNonNull(algo, "algo");
            return this;
        }

        public Builder setAuthenticatedEncryption(IpSecAlgorithm algo) {
            this.mAuthenticatedEncryption = Objects.requireNonNull(algo, "algo");
            return this;
        }

        public Builder setIpv4Encapsulation(
                IpSecManager.UdpEncapsulationSocket localSocket, int remotePort) {
            this.mUdpSocket = Objects.requireNonNull(localSocket, "localSocket");
            if (remotePort < 1 || remotePort > 65535) {
                throw new IllegalArgumentException("Invalid remotePort: " + remotePort);
            }
            this.mRemotePort = remotePort;
            return this;
        }

        public IpSecTransform buildTransportModeTransform(
                InetAddress sourceAddress, IpSecManager.SecurityParameterIndex spi)
                throws IOException,
                        IpSecManager.ResourceUnavailableException,
                        IpSecManager.SpiUnavailableException {
            Objects.requireNonNull(sourceAddress, "sourceAddress");
            Objects.requireNonNull(spi, "spi");
            if (mAuthenticatedEncryption != null
                    && (mEncryption != null || mAuthentication != null)) {
                throw new IllegalArgumentException(
                        "Authenticated encryption cannot be combined with separate encryption/authentication");
            }
            if (mAuthenticatedEncryption == null
                    && mEncryption == null
                    && mAuthentication == null) {
                throw new IllegalArgumentException("At least one IPsec algorithm is required");
            }
            return new IpSecTransform(sourceAddress, spi.getSpi());
        }
    }
}
