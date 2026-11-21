// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Streaming Base64 decoder supporting standard and URL-safe alphabets, whitespace skipping,
 * padding validation, and {@link Base64#NO_CLOSE}.
 */
public class Base64InputStream extends FilterInputStream {
    private static final int[] DECODE_STANDARD = new int[256];
    private static final int[] DECODE_URL_SAFE = new int[256];
    private static final int SKIP = -1;
    private static final int PAD = -2;
    private static final int INVALID = -3;

    static {
        Arrays.fill(DECODE_STANDARD, INVALID);
        Arrays.fill(DECODE_URL_SAFE, INVALID);
        String std = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        String url = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        for (int i = 0; i < 64; i++) {
            DECODE_STANDARD[std.charAt(i)] = i;
            DECODE_URL_SAFE[url.charAt(i)] = i;
        }
        for (int ws : new int[] {' ', '\t', '\r', '\n'}) {
            DECODE_STANDARD[ws] = SKIP;
            DECODE_URL_SAFE[ws] = SKIP;
        }
        DECODE_STANDARD['='] = PAD;
        DECODE_URL_SAFE['='] = PAD;
    }

    private final int flags;
    private final int[] alphabet;
    private final byte[] inputBuffer = new byte[2048];
    private final byte[] decodedBuffer = new byte[1540];
    private int decodedStart;
    private int decodedEnd;
    private final int[] quantum = new int[4];
    private int quantumPos;
    private int padCount;
    private boolean eof;
    private boolean closed;

    public Base64InputStream(InputStream in, int flags) {
        super(in);
        this.flags = flags;
        this.alphabet = (flags & Base64.URL_SAFE) != 0 ? DECODE_URL_SAFE : DECODE_STANDARD;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void mark(int readlimit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        eof = true;
        decodedStart = 0;
        decodedEnd = 0;
        if ((flags & Base64.NO_CLOSE) == 0 && in != null) {
            in.close();
        }
    }

    @Override
    public int available() {
        return eof ? (decodedEnd - decodedStart) : Math.max(1, decodedEnd - decodedStart);
    }

    @Override
    public long skip(long n) throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        if (n <= 0) {
            return 0;
        }
        byte[] scratch = new byte[(int) Math.min(2048L, n)];
        long remaining = n;
        while (remaining > 0) {
            int count = read(scratch, 0, (int) Math.min(scratch.length, remaining));
            if (count < 0) {
                break;
            }
            remaining -= count;
        }
        return n - remaining;
    }

    @Override
    public int read() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        if (decodedStart >= decodedEnd) {
            refill();
            if (decodedStart >= decodedEnd) {
                return -1;
            }
        }
        return decodedBuffer[decodedStart++] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        if (b == null) {
            throw new NullPointerException("buffer == null");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        int total = 0;
        while (total < len) {
            if (decodedStart >= decodedEnd) {
                refill();
                if (decodedStart >= decodedEnd) {
                    return total == 0 ? -1 : total;
                }
            }
            int chunk = Math.min(len - total, decodedEnd - decodedStart);
            System.arraycopy(decodedBuffer, decodedStart, b, off + total, chunk);
            decodedStart += chunk;
            total += chunk;
            if (eof) {
                break;
            }
        }
        return total;
    }

    private void refill() throws IOException {
        decodedStart = 0;
        decodedEnd = 0;
        if (eof) {
            return;
        }
        while (decodedEnd == 0 && !eof) {
            int bytesRead = in.read(inputBuffer, 0, inputBuffer.length);
            if (bytesRead < 0) {
                eof = true;
                finishAtEof();
                return;
            }
            decodeChunk(inputBuffer, bytesRead);
        }
    }

    private void decodeChunk(byte[] src, int length) throws Base64DataException {
        for (int i = 0; i < length; i++) {
            int symbol = alphabet[src[i] & 0xff];
            if (symbol == SKIP) {
                continue;
            }
            if (symbol == INVALID) {
                eof = true;
                throw new Base64DataException("bad base-64");
            }
            if (padCount > 0) {
                if (symbol != PAD) {
                    eof = true;
                    throw new Base64DataException("bad base-64");
                }
                padCount++;
                if (quantumPos + padCount == 4) {
                    flushPaddedQuantum();
                } else if (padCount > 2) {
                    eof = true;
                    throw new Base64DataException("bad base-64");
                }
                continue;
            }
            if (symbol == PAD) {
                if (quantumPos < 2) {
                    eof = true;
                    throw new Base64DataException("bad base-64");
                }
                padCount = 1;
                if (quantumPos + padCount == 4) {
                    flushPaddedQuantum();
                }
                continue;
            }
            if (padCount == -1) {
                // Saw trailing non-whitespace after padding completed.
                eof = true;
                throw new Base64DataException("bad base-64");
            }
            quantum[quantumPos++] = symbol;
            if (quantumPos == 4) {
                int triple =
                        (quantum[0] << 18)
                                | (quantum[1] << 12)
                                | (quantum[2] << 6)
                                | quantum[3];
                decodedBuffer[decodedEnd++] = (byte) (triple >> 16);
                decodedBuffer[decodedEnd++] = (byte) (triple >> 8);
                decodedBuffer[decodedEnd++] = (byte) triple;
                quantumPos = 0;
            }
        }
    }

    private void flushPaddedQuantum() throws Base64DataException {
        if (quantumPos == 2 && padCount == 2) {
            int value = (quantum[0] << 18) | (quantum[1] << 12);
            decodedBuffer[decodedEnd++] = (byte) (value >> 16);
        } else if (quantumPos == 3 && padCount == 1) {
            int value = (quantum[0] << 18) | (quantum[1] << 12) | (quantum[2] << 6);
            decodedBuffer[decodedEnd++] = (byte) (value >> 16);
            decodedBuffer[decodedEnd++] = (byte) (value >> 8);
        } else {
            eof = true;
            throw new Base64DataException("bad base-64");
        }
        quantumPos = 0;
        padCount = -1;
    }

    private void finishAtEof() throws Base64DataException {
        if (padCount > 0) {
            throw new Base64DataException("bad base-64");
        }
        if (quantumPos == 0) {
            return;
        }
        if (quantumPos == 1) {
            throw new Base64DataException("bad base-64");
        }
        if (quantumPos == 2) {
            int value = (quantum[0] << 18) | (quantum[1] << 12);
            decodedBuffer[decodedEnd++] = (byte) (value >> 16);
        } else if (quantumPos == 3) {
            int value = (quantum[0] << 18) | (quantum[1] << 12) | (quantum[2] << 6);
            decodedBuffer[decodedEnd++] = (byte) (value >> 16);
            decodedBuffer[decodedEnd++] = (byte) (value >> 8);
        }
        quantumPos = 0;
    }
}
