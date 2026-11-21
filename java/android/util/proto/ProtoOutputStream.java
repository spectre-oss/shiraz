// SPDX-License-Identifier: GPL-3.0-only
package android.util.proto;

import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Streaming Protocol Buffers binary wire-format writer matching Android API 35's
 * {@code ProtoOutputStream} field-ID encoding, nested message tokens, and wire types.
 */
public final class ProtoOutputStream {
    public static final int FIELD_ID_SHIFT = 3;
    public static final int FIELD_TYPE_SHIFT = 32;
    public static final int FIELD_COUNT_SHIFT = 40;

    public static final long FIELD_TYPE_DOUBLE = 1L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_FLOAT = 2L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_INT64 = 3L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_UINT64 = 4L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_INT32 = 5L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_FIXED64 = 6L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_FIXED32 = 7L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_BOOL = 8L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_STRING = 9L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_MESSAGE = 11L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_BYTES = 12L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_UINT32 = 13L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_ENUM = 14L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_SFIXED32 = 15L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_SFIXED64 = 16L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_SINT32 = 17L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_SINT64 = 18L << FIELD_TYPE_SHIFT;
    public static final long FIELD_TYPE_MASK = 0x0ffL << FIELD_TYPE_SHIFT;

    public static final long FIELD_COUNT_UNKNOWN = 0L;
    public static final long FIELD_COUNT_SINGLE = 1L << FIELD_COUNT_SHIFT;
    public static final long FIELD_COUNT_REPEATED = 2L << FIELD_COUNT_SHIFT;
    public static final long FIELD_COUNT_PACKED = 5L << FIELD_COUNT_SHIFT;
    public static final long FIELD_COUNT_MASK = 0x0fL << FIELD_COUNT_SHIFT;

    public static final int WIRE_TYPE_VARINT = 0;
    public static final int WIRE_TYPE_FIXED64 = 1;
    public static final int WIRE_TYPE_LENGTH_DELIMITED = 2;
    public static final int WIRE_TYPE_START_GROUP = 3;
    public static final int WIRE_TYPE_END_GROUP = 4;
    public static final int WIRE_TYPE_FIXED32 = 5;
    public static final int WIRE_TYPE_MASK = 7;

    private static final class MessageFrame {
        final long token;
        final int fieldNumber;
        final ByteArrayOutputStream buffer;

        MessageFrame(long token, int fieldNumber, int initialCapacity) {
            this.token = token;
            this.fieldNumber = fieldNumber;
            this.buffer = new ByteArrayOutputStream(initialCapacity);
        }
    }

    private final ByteArrayOutputStream mRootBuffer;
    private final OutputStream mStream;
    private final Deque<MessageFrame> mStack = new ArrayDeque<>();
    private int mNextObjectId = 1;
    private int mFlushedOffset;

    public ProtoOutputStream() {
        this(1024);
    }

    public ProtoOutputStream(int chunkSize) {
        if (chunkSize < 0) {
            throw new IllegalArgumentException("chunkSize < 0: " + chunkSize);
        }
        mRootBuffer = new ByteArrayOutputStream(Math.max(32, chunkSize));
        mStream = null;
    }

    public ProtoOutputStream(OutputStream stream) {
        if (stream == null) {
            throw new NullPointerException("stream == null");
        }
        mRootBuffer = new ByteArrayOutputStream(1024);
        mStream = stream;
    }

    private ByteArrayOutputStream currentBuffer() {
        MessageFrame top = mStack.peek();
        return top != null ? top.buffer : mRootBuffer;
    }

    public int getRawSize() {
        int total = mRootBuffer.size();
        for (MessageFrame frame : mStack) {
            total += frame.buffer.size();
        }
        return total;
    }

    public void write(long fieldId, double val) {
        long type = fieldId & FIELD_TYPE_MASK;
        int id = extractFieldNumber(fieldId);
        boolean single = isSingleCount(fieldId);
        if (type == FIELD_TYPE_DOUBLE) {
            if (!single || val != 0.0d) {
                writeTag(id, WIRE_TYPE_FIXED64);
                writeFixed64Raw(Double.doubleToRawLongBits(val));
            }
        } else if (type == FIELD_TYPE_FLOAT) {
            write(fieldId, (float) val);
        } else if (type == FIELD_TYPE_INT32 || type == FIELD_TYPE_ENUM
                || type == FIELD_TYPE_UINT32 || type == FIELD_TYPE_SINT32
                || type == FIELD_TYPE_FIXED32 || type == FIELD_TYPE_SFIXED32) {
            write(fieldId, (int) val);
        } else if (type == FIELD_TYPE_INT64 || type == FIELD_TYPE_UINT64
                || type == FIELD_TYPE_SINT64 || type == FIELD_TYPE_FIXED64
                || type == FIELD_TYPE_SFIXED64) {
            write(fieldId, (long) val);
        } else if (type == FIELD_TYPE_BOOL) {
            write(fieldId, val != 0.0d);
        } else {
            throw badFieldType("write(long, double)", fieldId);
        }
    }

    public void write(long fieldId, float val) {
        long type = fieldId & FIELD_TYPE_MASK;
        int id = extractFieldNumber(fieldId);
        boolean single = isSingleCount(fieldId);
        if (type == FIELD_TYPE_FLOAT) {
            if (!single || val != 0.0f) {
                writeTag(id, WIRE_TYPE_FIXED32);
                writeFixed32Raw(Float.floatToRawIntBits(val));
            }
        } else if (type == FIELD_TYPE_DOUBLE) {
            write(fieldId, (double) val);
        } else if (type == FIELD_TYPE_INT32 || type == FIELD_TYPE_ENUM
                || type == FIELD_TYPE_UINT32 || type == FIELD_TYPE_SINT32
                || type == FIELD_TYPE_FIXED32 || type == FIELD_TYPE_SFIXED32) {
            write(fieldId, (int) val);
        } else if (type == FIELD_TYPE_INT64 || type == FIELD_TYPE_UINT64
                || type == FIELD_TYPE_SINT64 || type == FIELD_TYPE_FIXED64
                || type == FIELD_TYPE_SFIXED64) {
            write(fieldId, (long) val);
        } else if (type == FIELD_TYPE_BOOL) {
            write(fieldId, val != 0.0f);
        } else {
            throw badFieldType("write(long, float)", fieldId);
        }
    }

    public void write(long fieldId, int val) {
        long type = fieldId & FIELD_TYPE_MASK;
        int id = extractFieldNumber(fieldId);
        boolean single = isSingleCount(fieldId);
        if (type == FIELD_TYPE_INT32 || type == FIELD_TYPE_ENUM) {
            if (!single || val != 0) {
                writeTag(id, WIRE_TYPE_VARINT);
                // Negative int32/enum values in protobuf varint are sign-extended to 64 bits (10 bytes).
                writeVarint64Raw(val);
            }
        } else if (type == FIELD_TYPE_UINT32) {
            if (!single || val != 0) {
                writeTag(id, WIRE_TYPE_VARINT);
                writeVarint64Raw(val & 0xffffffffL);
            }
        } else if (type == FIELD_TYPE_SINT32) {
            if (!single || val != 0) {
                writeTag(id, WIRE_TYPE_VARINT);
                writeVarint64Raw(((val << 1) ^ (val >> 31)) & 0xffffffffL);
            }
        } else if (type == FIELD_TYPE_FIXED32 || type == FIELD_TYPE_SFIXED32) {
            if (!single || val != 0) {
                writeTag(id, WIRE_TYPE_FIXED32);
                writeFixed32Raw(val);
            }
        } else if (type == FIELD_TYPE_INT64 || type == FIELD_TYPE_UINT64
                || type == FIELD_TYPE_SINT64 || type == FIELD_TYPE_FIXED64
                || type == FIELD_TYPE_SFIXED64) {
            write(fieldId, (long) val);
        } else if (type == FIELD_TYPE_FLOAT) {
            write(fieldId, (float) val);
        } else if (type == FIELD_TYPE_DOUBLE) {
            write(fieldId, (double) val);
        } else if (type == FIELD_TYPE_BOOL) {
            write(fieldId, val != 0);
        } else {
            throw badFieldType("write(long, int)", fieldId);
        }
    }

    public void write(long fieldId, long val) {
        long type = fieldId & FIELD_TYPE_MASK;
        int id = extractFieldNumber(fieldId);
        boolean single = isSingleCount(fieldId);
        if (type == FIELD_TYPE_INT64 || type == FIELD_TYPE_UINT64) {
            if (!single || val != 0L) {
                writeTag(id, WIRE_TYPE_VARINT);
                writeVarint64Raw(val);
            }
        } else if (type == FIELD_TYPE_SINT64) {
            if (!single || val != 0L) {
                writeTag(id, WIRE_TYPE_VARINT);
                writeVarint64Raw((val << 1) ^ (val >> 63));
            }
        } else if (type == FIELD_TYPE_FIXED64 || type == FIELD_TYPE_SFIXED64) {
            if (!single || val != 0L) {
                writeTag(id, WIRE_TYPE_FIXED64);
                writeFixed64Raw(val);
            }
        } else if (type == FIELD_TYPE_INT32 || type == FIELD_TYPE_UINT32
                || type == FIELD_TYPE_SINT32 || type == FIELD_TYPE_FIXED32
                || type == FIELD_TYPE_SFIXED32 || type == FIELD_TYPE_ENUM) {
            write(fieldId, (int) val);
        } else if (type == FIELD_TYPE_DOUBLE) {
            write(fieldId, (double) val);
        } else if (type == FIELD_TYPE_FLOAT) {
            write(fieldId, (float) val);
        } else if (type == FIELD_TYPE_BOOL) {
            write(fieldId, val != 0L);
        } else {
            throw badFieldType("write(long, long)", fieldId);
        }
    }

    public void write(long fieldId, boolean val) {
        long type = fieldId & FIELD_TYPE_MASK;
        int id = extractFieldNumber(fieldId);
        boolean single = isSingleCount(fieldId);
        if (type == FIELD_TYPE_BOOL) {
            if (!single || val) {
                writeTag(id, WIRE_TYPE_VARINT);
                currentBuffer().write(val ? 1 : 0);
            }
        } else {
            throw badFieldType("write(long, boolean)", fieldId);
        }
    }

    public void write(long fieldId, String val) {
        long type = fieldId & FIELD_TYPE_MASK;
        int id = extractFieldNumber(fieldId);
        boolean single = isSingleCount(fieldId);
        if (type != FIELD_TYPE_STRING) {
            throw badFieldType("write(long, String)", fieldId);
        }
        if (single) {
            if (val != null && !val.isEmpty()) {
                byte[] utf8 = val.getBytes(StandardCharsets.UTF_8);
                writeLengthDelimitedRaw(id, utf8);
            }
        } else {
            byte[] utf8 = val == null ? new byte[0] : val.getBytes(StandardCharsets.UTF_8);
            writeLengthDelimitedRaw(id, utf8);
        }
    }

    public void write(long fieldId, byte[] val) {
        long type = fieldId & FIELD_TYPE_MASK;
        int id = extractFieldNumber(fieldId);
        boolean single = isSingleCount(fieldId);
        if (type != FIELD_TYPE_BYTES && type != FIELD_TYPE_MESSAGE) {
            throw badFieldType("write(long, byte[])", fieldId);
        }
        if (single) {
            if (val != null && val.length > 0) {
                writeLengthDelimitedRaw(id, val);
            }
        } else {
            writeLengthDelimitedRaw(id, val == null ? new byte[0] : val);
        }
    }

    public long start(long fieldId) {
        if ((fieldId & FIELD_TYPE_MASK) != FIELD_TYPE_MESSAGE) {
            throw badFieldType("start(long)", fieldId);
        }
        int id = extractFieldNumber(fieldId);
        boolean repeated = (fieldId & FIELD_COUNT_MASK) == FIELD_COUNT_REPEATED;
        int depth = mStack.size() + 1;
        int objectId = mNextObjectId++;
        int tagSize = varintSize(((long) id << FIELD_ID_SHIFT) | WIRE_TYPE_LENGTH_DELIMITED);
        int sizePos = currentBuffer().size();
        long token = makeToken(tagSize, repeated, depth, objectId, sizePos);
        mStack.push(new MessageFrame(token, id, 128));
        return token;
    }

    public void end(long token) {
        MessageFrame top = mStack.peek();
        if (top == null || top.token != token) {
            throw new IllegalStateException(
                    "Unexpected end token " + token2String(token)
                            + " (top=" + (top == null ? "none" : token2String(top.token)) + ")");
        }
        mStack.pop();
        byte[] childBytes = top.buffer.toByteArray();
        writeLengthDelimitedRaw(top.fieldNumber, childBytes);
    }

    public static long makeFieldId(int id, long fieldFlags) {
        return fieldFlags | (id & 0xffffffffL);
    }

    public static int checkFieldId(long fieldId, long expectedFlags) {
        long fieldCount = fieldId & FIELD_COUNT_MASK;
        long fieldType = fieldId & FIELD_TYPE_MASK;
        long expectedCount = expectedFlags & FIELD_COUNT_MASK;
        long expectedType = expectedFlags & FIELD_TYPE_MASK;
        if ((int) fieldId == 0) {
            throw new IllegalArgumentException(
                    "Invalid proto field " + (int) fieldId + " fieldId=" + Long.toHexString(fieldId));
        }
        if (fieldType != expectedType || (expectedCount != 0 && fieldCount != expectedCount)) {
            throw new IllegalArgumentException(
                    "Unexpected proto field " + getFieldIdString(fieldId)
                            + " expected " + getFieldCountString(expectedCount)
                            + " " + getFieldTypeString(expectedType));
        }
        return (int) fieldId;
    }

    public void writeTag(int id, int wireType) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid field id: " + id);
        }
        writeVarint64Raw((((long) id) << FIELD_ID_SHIFT) | (wireType & WIRE_TYPE_MASK));
    }

    public byte[] getBytes() {
        if (!mStack.isEmpty()) {
            throw new IllegalStateException("Unclosed nested message frames: " + mStack.size());
        }
        flush();
        return mRootBuffer.toByteArray();
    }

    public void flush() {
        if (mStream == null || !mStack.isEmpty()) {
            return;
        }
        byte[] all = mRootBuffer.toByteArray();
        if (all.length > mFlushedOffset) {
            try {
                mStream.write(all, mFlushedOffset, all.length - mFlushedOffset);
                mStream.flush();
                mFlushedOffset = all.length;
            } catch (IOException e) {
                throw new RuntimeException("Failed flushing ProtoOutputStream", e);
            }
        }
    }

    public void dump(String tag) {
        Log.d(tag, "ProtoOutputStream{rawSize=" + getRawSize() + ", openDepth=" + mStack.size() + "}");
    }

    public static String token2String(long token) {
        if (token == 0L) {
            return "Token(null)";
        }
        int tagSize = (int) ((token >>> 61) & 0x07L);
        boolean repeated = ((token >>> 60) & 0x01L) != 0;
        int depth = (int) ((token >>> 51) & 0x1ffL);
        int objectId = (int) ((token >>> 32) & 0x7ffffL);
        int sizePos = (int) token;
        return "Token(tagSize=" + tagSize
                + ", repeated=" + repeated
                + ", depth=" + depth
                + ", objectId=" + objectId
                + ", sizePos=" + sizePos + ")";
    }

    public static String getFieldTypeString(long fieldType) {
        long masked = fieldType & FIELD_TYPE_MASK;
        if (masked == FIELD_TYPE_DOUBLE) return "double";
        if (masked == FIELD_TYPE_FLOAT) return "float";
        if (masked == FIELD_TYPE_INT64) return "int64";
        if (masked == FIELD_TYPE_UINT64) return "uint64";
        if (masked == FIELD_TYPE_INT32) return "int32";
        if (masked == FIELD_TYPE_FIXED64) return "fixed64";
        if (masked == FIELD_TYPE_FIXED32) return "fixed32";
        if (masked == FIELD_TYPE_BOOL) return "bool";
        if (masked == FIELD_TYPE_STRING) return "string";
        if (masked == FIELD_TYPE_MESSAGE) return "message";
        if (masked == FIELD_TYPE_BYTES) return "bytes";
        if (masked == FIELD_TYPE_UINT32) return "uint32";
        if (masked == FIELD_TYPE_ENUM) return "enum";
        if (masked == FIELD_TYPE_SFIXED32) return "sfixed32";
        if (masked == FIELD_TYPE_SFIXED64) return "sfixed64";
        if (masked == FIELD_TYPE_SINT32) return "sint32";
        if (masked == FIELD_TYPE_SINT64) return "sint64";
        return null;
    }

    public static String getWireTypeString(int wireType) {
        switch (wireType) {
            case WIRE_TYPE_VARINT:
                return "Varint";
            case WIRE_TYPE_FIXED64:
                return "Fixed64";
            case WIRE_TYPE_LENGTH_DELIMITED:
                return "Length Delimited";
            case WIRE_TYPE_START_GROUP:
                return "Start Group";
            case WIRE_TYPE_END_GROUP:
                return "End Group";
            case WIRE_TYPE_FIXED32:
                return "Fixed32";
            default:
                return null;
        }
    }

    public static String getFieldIdString(long fieldId) {
        String count = getFieldCountString(fieldId & FIELD_COUNT_MASK);
        String type = getFieldTypeString(fieldId & FIELD_TYPE_MASK);
        return (count != null ? count + " " : "")
                + (type != null ? type + " " : "")
                + "tag=" + ((int) fieldId)
                + " (0x" + Long.toHexString(fieldId) + ")";
    }

    public static String getFieldCountString(long fieldCount) {
        long masked = fieldCount & FIELD_COUNT_MASK;
        if (masked == FIELD_COUNT_UNKNOWN) return "";
        if (masked == FIELD_COUNT_SINGLE) return "optional";
        if (masked == FIELD_COUNT_REPEATED) return "repeated";
        if (masked == FIELD_COUNT_PACKED) return "packed";
        return null;
    }

    public static long makeToken(int tagSize, boolean repeated, int depth, int objectId, int sizePos) {
        return ((tagSize & 0x07L) << 61)
                | (repeated ? (1L << 60) : 0L)
                | ((depth & 0x01ffL) << 51)
                | ((objectId & 0x07ffffL) << 32)
                | (sizePos & 0xffffffffL);
    }

    private static int extractFieldNumber(long fieldId) {
        int id = (int) fieldId;
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid proto field number in 0x" + Long.toHexString(fieldId));
        }
        return id;
    }

    private static boolean isSingleCount(long fieldId) {
        long count = fieldId & FIELD_COUNT_MASK;
        if (count == FIELD_COUNT_SINGLE || count == FIELD_COUNT_UNKNOWN) {
            return true;
        }
        if (count == FIELD_COUNT_REPEATED || count == FIELD_COUNT_PACKED) {
            return false;
        }
        throw new IllegalArgumentException("Invalid field count in " + getFieldIdString(fieldId));
    }

    private static IllegalArgumentException badFieldType(String method, long fieldId) {
        return new IllegalArgumentException(
                "Cannot call " + method + " with field " + getFieldIdString(fieldId));
    }

    private void writeLengthDelimitedRaw(int id, byte[] bytes) {
        writeTag(id, WIRE_TYPE_LENGTH_DELIMITED);
        writeVarint64Raw(bytes.length);
        currentBuffer().write(bytes, 0, bytes.length);
    }

    private void writeVarint64Raw(long value) {
        ByteArrayOutputStream out = currentBuffer();
        long v = value;
        while ((v & ~0x7fL) != 0L) {
            out.write((int) ((v & 0x7fL) | 0x80L));
            v >>>= 7;
        }
        out.write((int) v);
    }

    private void writeFixed32Raw(int value) {
        ByteArrayOutputStream out = currentBuffer();
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    private void writeFixed64Raw(long value) {
        ByteArrayOutputStream out = currentBuffer();
        for (int shift = 0; shift < 64; shift += 8) {
            out.write((int) ((value >>> shift) & 0xffL));
        }
    }

    private static int varintSize(long v) {
        int count = 1;
        long u = v;
        while ((u & ~0x7fL) != 0L) {
            count++;
            u >>>= 7;
        }
        return count;
    }
}
