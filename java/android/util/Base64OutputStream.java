package android.util;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Buffers written bytes and emits the encoded or decoded form to the target on close. */
public class Base64OutputStream extends FilterOutputStream {
    private final int flags;
    private final boolean encode;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean closed;

    public Base64OutputStream(OutputStream out, int flags) { this(out, flags, true); }

    public Base64OutputStream(OutputStream out, int flags, boolean encode) {
        super(out);
        this.flags = flags;
        this.encode = encode;
    }

    @Override public void write(int b) throws IOException { buffer.write(b); }
    @Override public void write(byte[] b, int off, int len) throws IOException { buffer.write(b, off, len); }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        byte[] data = buffer.toByteArray();
        out.write(encode ? Base64.encode(data, flags) : Base64.decode(data, flags));
        out.flush();
        if ((flags & Base64.NO_CLOSE) == 0) out.close();
    }
}
