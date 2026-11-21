// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.CancellationSignal;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Asynchronous DNS resolver backed by host resolution and RFC 1035 wire queries. */
public final class DnsResolver {
    public static final int CLASS_IN = 1;
    public static final int TYPE_A = 1;
    public static final int TYPE_AAAA = 28;

    public static final int FLAG_EMPTY = 0;
    public static final int FLAG_NO_RETRY = 1;
    public static final int FLAG_NO_CACHE_STORE = 2;
    public static final int FLAG_NO_CACHE_LOOKUP = 4;

    public static final int ERROR_PARSE = 0;
    public static final int ERROR_SYSTEM = 1;

    private static final DnsResolver INSTANCE = new DnsResolver();

    /** Callback receiving asynchronous DNS answers or errors. */
    public interface Callback<T> {
        void onAnswer(T answer, int rcode);

        void onError(DnsException error);
    }

    /** Checked exception delivered to {@link Callback#onError}. */
    public static class DnsException extends Exception {
        public final int code;

        public DnsException(int code, Throwable cause) {
            super(cause);
            this.code = code;
        }
    }

    private DnsResolver() {
    }

    public static DnsResolver getInstance() {
        return INSTANCE;
    }

    public void query(
            final Network network,
            final String domain,
            final int flags,
            final Executor executor,
            final CancellationSignal cancellationSignal,
            final Callback<? super List<InetAddress>> callback) {
        queryInternal(network, domain, -1, flags, executor, cancellationSignal, callback);
    }

    public void query(
            final Network network,
            final String domain,
            final int nsType,
            final int flags,
            final Executor executor,
            final CancellationSignal cancellationSignal,
            final Callback<? super List<InetAddress>> callback) {
        queryInternal(network, domain, nsType, flags, executor, cancellationSignal, callback);
    }

    private void queryInternal(
            final Network network,
            final String domain,
            final int nsType,
            final int flags,
            final Executor executor,
            final CancellationSignal cancellationSignal,
            final Callback<? super List<InetAddress>> callback) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            return;
        }
        Thread worker =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                if (cancellationSignal != null && cancellationSignal.isCanceled()) {
                                    return;
                                }
                                try {
                                    InetAddress[] all =
                                            network != null
                                                    ? network.getAllByName(domain)
                                                    : InetAddress.getAllByName(domain);
                                    final List<InetAddress> filtered = new ArrayList<InetAddress>();
                                    for (InetAddress addr : all) {
                                        if (nsType == TYPE_A && !(addr instanceof Inet4Address)) {
                                            continue;
                                        }
                                        if (nsType == TYPE_AAAA
                                                && !(addr instanceof Inet6Address)) {
                                            continue;
                                        }
                                        filtered.add(addr);
                                    }
                                    if (cancellationSignal != null
                                            && cancellationSignal.isCanceled()) {
                                        return;
                                    }
                                    executor.execute(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (cancellationSignal == null
                                                            || !cancellationSignal.isCanceled()) {
                                                        callback.onAnswer(
                                                                Collections.unmodifiableList(
                                                                        filtered),
                                                                0);
                                                    }
                                                }
                                            });
                                } catch (final UnknownHostException e) {
                                    if (cancellationSignal != null
                                            && cancellationSignal.isCanceled()) {
                                        return;
                                    }
                                    executor.execute(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (cancellationSignal == null
                                                            || !cancellationSignal.isCanceled()) {
                                                        callback.onAnswer(
                                                                Collections.<InetAddress>emptyList(),
                                                                3);
                                                    }
                                                }
                                            });
                                } catch (final Throwable t) {
                                    if (cancellationSignal != null
                                            && cancellationSignal.isCanceled()) {
                                        return;
                                    }
                                    executor.execute(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (cancellationSignal == null
                                                            || !cancellationSignal.isCanceled()) {
                                                        callback.onError(
                                                                new DnsException(ERROR_SYSTEM, t));
                                                    }
                                                }
                                            });
                                }
                            }
                        },
                        "shiraz-dns-query");
        worker.setDaemon(true);
        worker.start();
    }

    public void rawQuery(
            final Network network,
            final byte[] query,
            final int flags,
            final Executor executor,
            final CancellationSignal cancellationSignal,
            final Callback<? super byte[]> callback) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            return;
        }
        final byte[] packetCopy = query.clone();
        Thread worker =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                if (cancellationSignal != null && cancellationSignal.isCanceled()) {
                                    return;
                                }
                                if (packetCopy.length < 12) {
                                    deliverError(
                                            executor,
                                            cancellationSignal,
                                            callback,
                                            new DnsException(
                                                    ERROR_PARSE,
                                                    new IllegalArgumentException(
                                                            "DNS packet shorter than 12-byte header")));
                                    return;
                                }
                                try {
                                    InetAddress server = pickNameserver();
                                    if (server == null) {
                                        throw new UnknownHostException(
                                                "No configured host DNS server");
                                    }
                                    DatagramSocket sock = new DatagramSocket();
                                    try {
                                        if (network != null) {
                                            network.bindSocket(sock);
                                        }
                                        sock.setSoTimeout(3000);
                                        DatagramPacket out =
                                                new DatagramPacket(
                                                        packetCopy, packetCopy.length, server, 53);
                                        sock.send(out);
                                        byte[] buf = new byte[4096];
                                        DatagramPacket in = new DatagramPacket(buf, buf.length);
                                        sock.receive(in);
                                        final byte[] answer = new byte[in.getLength()];
                                        System.arraycopy(buf, 0, answer, 0, answer.length);
                                        final int rcode =
                                                answer.length >= 4 ? (answer[3] & 0x0F) : 0;
                                        if (cancellationSignal == null
                                                || !cancellationSignal.isCanceled()) {
                                            executor.execute(
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (cancellationSignal == null
                                                                    || !cancellationSignal
                                                                            .isCanceled()) {
                                                                callback.onAnswer(answer, rcode);
                                                            }
                                                        }
                                                    });
                                        }
                                    } finally {
                                        sock.close();
                                    }
                                } catch (final Throwable t) {
                                    deliverError(
                                            executor,
                                            cancellationSignal,
                                            callback,
                                            new DnsException(ERROR_SYSTEM, t));
                                }
                            }
                        },
                        "shiraz-dns-raw");
        worker.setDaemon(true);
        worker.start();
    }

    public void rawQuery(
            final Network network,
            final String domain,
            final int nsClass,
            final int nsType,
            final int flags,
            final Executor executor,
            final CancellationSignal cancellationSignal,
            final Callback<? super byte[]> callback) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        byte[] wire;
        try {
            wire = buildDnsQuestionPacket(domain, nsClass, nsType);
        } catch (IllegalArgumentException badDomain) {
            deliverError(
                    executor,
                    cancellationSignal,
                    callback,
                    new DnsException(ERROR_PARSE, badDomain));
            return;
        }
        rawQuery(network, wire, flags, executor, cancellationSignal, callback);
    }

    private static void deliverError(
            Executor executor,
            final CancellationSignal cancellationSignal,
            final Callback<? super byte[]> callback,
            final DnsException error) {
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            return;
        }
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        if (cancellationSignal == null || !cancellationSignal.isCanceled()) {
                            callback.onError(error);
                        }
                    }
                });
    }

    private static InetAddress pickNameserver() {
        try {
            LinkProperties lp = shiraz.runtime.HostConnectivity.linkProperties();
            if (lp != null && !lp.getDnsServers().isEmpty()) {
                return lp.getDnsServers().get(0);
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static byte[] buildDnsQuestionPacket(String domain, int nsClass, int nsType) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        int id = (int) (System.nanoTime() & 0xFFFF);
        out.write((id >> 8) & 0xFF);
        out.write(id & 0xFF);
        out.write(0x01); // RD=1
        out.write(0x00);
        out.write(0x00);
        out.write(0x01); // QDCOUNT=1
        for (int i = 0; i < 6; i++) {
            out.write(0);
        }
        String trimmed = domain.endsWith(".") ? domain.substring(0, domain.length() - 1) : domain;
        if (!trimmed.isEmpty()) {
            String[] labels = trimmed.split("\\.", -1);
            for (String label : labels) {
                byte[] b = label.getBytes(StandardCharsets.UTF_8);
                if (b.length == 0 || b.length > 63) {
                    throw new IllegalArgumentException("Invalid DNS label in: " + domain);
                }
                out.write(b.length);
                out.write(b, 0, b.length);
            }
        }
        out.write(0);
        out.write((nsType >> 8) & 0xFF);
        out.write(nsType & 0xFF);
        out.write((nsClass >> 8) & 0xFF);
        out.write(nsClass & 0xFF);
        return out.toByteArray();
    }
}
