// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;

/** Per-thread accounting tag/UID selection state. Host socket tagging/accounting is not implemented. */
public class TrafficStats {
    public static final int UNSUPPORTED = -1;

    private static final ThreadLocal<Integer> TAG =
            new ThreadLocal<Integer>() {
                @Override
                protected Integer initialValue() {
                    return -1;
                }
            };

    private static final ThreadLocal<Integer> UID =
            new ThreadLocal<Integer>() {
                @Override
                protected Integer initialValue() {
                    return -1;
                }
            };

    public TrafficStats() {
    }

    // Selection alone does not attribute sockets or grant cross-UID accounting authority.
    public static void setThreadStatsUid(int uid) {
        UID.set(uid);
    }

    public static int getThreadStatsUid() {
        return UID.get();
    }

    public static void setThreadStatsUidSelf() {
        UID.set(android.os.Process.myUid());
    }

    public static void clearThreadStatsUid() {
        UID.remove();
    }

    public static void setThreadStatsTag(int tag) {
        TAG.set(tag);
    }

    public static int getThreadStatsTag() {
        return TAG.get();
    }

    public static int getAndSetThreadStatsTag(int tag) {
        int previous = TAG.get();
        TAG.set(tag);
        return previous;
    }

    public static void clearThreadStatsTag() {
        TAG.remove();
    }

    public static void tagSocket(Socket socket) throws SocketException {
        Objects.requireNonNull(socket, "socket");
    }

    public static void untagSocket(Socket socket) throws SocketException {
        Objects.requireNonNull(socket, "socket");
    }

    public static void tagDatagramSocket(DatagramSocket socket) throws SocketException {
        Objects.requireNonNull(socket, "socket");
    }

    public static void untagDatagramSocket(DatagramSocket socket) throws SocketException {
        Objects.requireNonNull(socket, "socket");
    }

    public static void tagFileDescriptor(FileDescriptor fd) throws IOException {
        Objects.requireNonNull(fd, "fd");
    }

    public static void untagFileDescriptor(FileDescriptor fd) throws IOException {
        Objects.requireNonNull(fd, "fd");
    }

    public static void incrementOperationCount(int operationCount) {
    }

    public static void incrementOperationCount(int tag, int operationCount) {
    }

    public static long getMobileTxPackets() {
        return UNSUPPORTED;
    }

    public static long getMobileRxPackets() {
        return UNSUPPORTED;
    }

    public static long getMobileTxBytes() {
        return UNSUPPORTED;
    }

    public static long getMobileRxBytes() {
        return UNSUPPORTED;
    }

    public static long getTxPackets(String iface) {
        Objects.requireNonNull(iface, "iface");
        return UNSUPPORTED;
    }

    public static long getRxPackets(String iface) {
        Objects.requireNonNull(iface, "iface");
        return UNSUPPORTED;
    }

    public static long getTxBytes(String iface) {
        Objects.requireNonNull(iface, "iface");
        return UNSUPPORTED;
    }

    public static long getRxBytes(String iface) {
        Objects.requireNonNull(iface, "iface");
        return UNSUPPORTED;
    }

    public static long getTotalTxPackets() {
        return UNSUPPORTED;
    }

    public static long getTotalRxPackets() {
        return UNSUPPORTED;
    }

    public static long getTotalTxBytes() {
        return UNSUPPORTED;
    }

    public static long getTotalRxBytes() {
        return UNSUPPORTED;
    }

    public static long getUidTxBytes(int uid) {
        return UNSUPPORTED;
    }

    public static long getUidRxBytes(int uid) {
        return UNSUPPORTED;
    }

    public static long getUidTxPackets(int uid) {
        return UNSUPPORTED;
    }

    public static long getUidRxPackets(int uid) {
        return UNSUPPORTED;
    }

    @Deprecated
    public static long getUidTcpTxBytes(int uid) {
        return UNSUPPORTED;
    }

    @Deprecated
    public static long getUidTcpRxBytes(int uid) {
        return UNSUPPORTED;
    }

    @Deprecated
    public static long getUidUdpTxBytes(int uid) {
        return UNSUPPORTED;
    }

    @Deprecated
    public static long getUidUdpRxBytes(int uid) {
        return UNSUPPORTED;
    }

    @Deprecated
    public static long getUidTcpTxSegments(int uid) {
        return UNSUPPORTED;
    }

    @Deprecated
    public static long getUidTcpRxSegments(int uid) {
        return UNSUPPORTED;
    }

    @Deprecated
    public static long getUidUdpTxPackets(int uid) {
        return UNSUPPORTED;
    }

    @Deprecated
    public static long getUidUdpRxPackets(int uid) {
        return UNSUPPORTED;
    }
}
