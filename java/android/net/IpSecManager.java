// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.util.AndroidException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Manager for IPsec Security Parameter Indices, UDP encapsulation sockets, and transforms. */
public class IpSecManager {
    public static final int DIRECTION_IN = 0;
    public static final int DIRECTION_OUT = 1;

    private static final AtomicInteger NEXT_SPI = new AtomicInteger(0x1000);

    IpSecManager() {
    }

    public static final class SpiUnavailableException extends AndroidException {
        private final int mSpi;

        SpiUnavailableException(String msg, int spi) {
            super(msg + " (spi: " + spi + ")");
            this.mSpi = spi;
        }

        public int getSpi() {
            return mSpi;
        }
    }

    public static final class ResourceUnavailableException extends AndroidException {
        ResourceUnavailableException(String msg) {
            super(msg);
        }
    }

    public static final class SecurityParameterIndex implements AutoCloseable {
        private final InetAddress mDestinationAddress;
        private int mSpi;

        SecurityParameterIndex(InetAddress destinationAddress, int spi) {
            this.mDestinationAddress = destinationAddress;
            this.mSpi = spi;
        }

        public int getSpi() {
            return mSpi;
        }

        @Override
        public void close() {
            mSpi = 0;
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                close();
            } finally {
                super.finalize();
            }
        }

        @Override
        public String toString() {
            return "SecurityParameterIndex{spi=" + mSpi + ", dst=" + mDestinationAddress + "}";
        }
    }

    public static final class UdpEncapsulationSocket implements AutoCloseable {
        private final DatagramSocket mSocket;

        UdpEncapsulationSocket(int port) throws IOException {
            this.mSocket = port == 0 ? new DatagramSocket() : new DatagramSocket(port);
        }

        public FileDescriptor getFileDescriptor() {
            return null;
        }

        public int getPort() {
            return mSocket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            mSocket.close();
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                close();
            } finally {
                super.finalize();
            }
        }

        @Override
        public String toString() {
            return "UdpEncapsulationSocket{port=" + getPort() + "}";
        }
    }

    public SecurityParameterIndex allocateSecurityParameterIndex(InetAddress destinationAddress)
            throws ResourceUnavailableException {
        Objects.requireNonNull(destinationAddress, "destinationAddress");
        return new SecurityParameterIndex(destinationAddress, NEXT_SPI.getAndIncrement());
    }

    public SecurityParameterIndex allocateSecurityParameterIndex(
            InetAddress destinationAddress, int requestedSpi)
            throws ResourceUnavailableException, SpiUnavailableException {
        Objects.requireNonNull(destinationAddress, "destinationAddress");
        if (requestedSpi < 256) {
            throw new IllegalArgumentException("Requested SPI must be >= 256");
        }
        return new SecurityParameterIndex(destinationAddress, requestedSpi);
    }

    public void applyTransportModeTransform(Socket socket, int direction, IpSecTransform transform)
            throws IOException {
        Objects.requireNonNull(socket, "socket");
        Objects.requireNonNull(transform, "transform");
        checkDirection(direction);
        throw new IOException("Kernel IPsec XFRM transport is unavailable on this host");
    }

    public void applyTransportModeTransform(
            DatagramSocket socket, int direction, IpSecTransform transform) throws IOException {
        Objects.requireNonNull(socket, "socket");
        Objects.requireNonNull(transform, "transform");
        checkDirection(direction);
        throw new IOException("Kernel IPsec XFRM transport is unavailable on this host");
    }

    public void applyTransportModeTransform(
            FileDescriptor socket, int direction, IpSecTransform transform) throws IOException {
        Objects.requireNonNull(socket, "socket");
        Objects.requireNonNull(transform, "transform");
        checkDirection(direction);
        throw new IOException("Kernel IPsec XFRM transport is unavailable on this host");
    }

    public void removeTransportModeTransforms(Socket socket) throws IOException {
        Objects.requireNonNull(socket, "socket");
    }

    public void removeTransportModeTransforms(DatagramSocket socket) throws IOException {
        Objects.requireNonNull(socket, "socket");
    }

    public void removeTransportModeTransforms(FileDescriptor socket) throws IOException {
        Objects.requireNonNull(socket, "socket");
    }

    public UdpEncapsulationSocket openUdpEncapsulationSocket(int port)
            throws IOException, ResourceUnavailableException {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid UDP port: " + port);
        }
        return new UdpEncapsulationSocket(port);
    }

    public UdpEncapsulationSocket openUdpEncapsulationSocket()
            throws IOException, ResourceUnavailableException {
        return new UdpEncapsulationSocket(0);
    }

    private static void checkDirection(int direction) {
        if (direction != DIRECTION_IN && direction != DIRECTION_OUT) {
            throw new IllegalArgumentException("Invalid direction: " + direction);
        }
    }
}
