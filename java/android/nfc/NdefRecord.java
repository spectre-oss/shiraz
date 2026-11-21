// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public final class NdefRecord implements Parcelable {
    public static final short TNF_EMPTY = 0x00;
    public static final short TNF_WELL_KNOWN = 0x01;
    public static final short TNF_MIME_MEDIA = 0x02;
    public static final short TNF_ABSOLUTE_URI = 0x03;
    public static final short TNF_EXTERNAL_TYPE = 0x04;
    public static final short TNF_UNKNOWN = 0x05;
    public static final short TNF_UNCHANGED = 0x06;

    public static final byte[] RTD_TEXT = {0x54}; // "T"
    public static final byte[] RTD_URI = {0x55}; // "U"
    public static final byte[] RTD_SMART_POSTER = {0x53, 0x70}; // "Sp"
    public static final byte[] RTD_ALTERNATIVE_CARRIER = {0x61, 0x63}; // "ac"
    public static final byte[] RTD_HANDOVER_CARRIER = {0x48, 0x63}; // "Hc"
    public static final byte[] RTD_HANDOVER_REQUEST = {0x48, 0x72}; // "Hr"
    public static final byte[] RTD_HANDOVER_SELECT = {0x48, 0x73}; // "Hs"

    private static final byte FLAG_MB = (byte) 0x80;
    private static final byte FLAG_ME = (byte) 0x40;
    private static final byte FLAG_CF = (byte) 0x20;
    private static final byte FLAG_SR = (byte) 0x10;
    private static final byte FLAG_IL = (byte) 0x08;

    private static final int MAX_PAYLOAD_SIZE = 10 * (1 << 20); // 10 MiB safety limit

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static final byte[] RTD_ANDROID_APP =
            "android.com:pkg".getBytes(StandardCharsets.US_ASCII);

    private static final String[] URI_PREFIX_MAP = new String[]{
        "", // 0x00
        "http://www.", // 0x01
        "https://www.", // 0x02
        "http://", // 0x03
        "https://", // 0x04
        "tel:", // 0x05
        "mailto:", // 0x06
        "ftp://anonymous:anonymous@", // 0x07
        "ftp://ftp.", // 0x08
        "ftps://", // 0x09
        "sftp://", // 0x0A
        "smb://", // 0x0B
        "nfs://", // 0x0C
        "ftp://", // 0x0D
        "dav://", // 0x0E
        "news:", // 0x0F
        "telnet://", // 0x10
        "imap:", // 0x11
        "rtsp://", // 0x12
        "urn:", // 0x13
        "pop:", // 0x14
        "sip:", // 0x15
        "sips:", // 0x16
        "tftp:", // 0x17
        "btspp://", // 0x18
        "btl2cap://", // 0x19
        "btgoep://", // 0x1A
        "tcpobex://", // 0x1B
        "irdaobex://", // 0x1C
        "file://", // 0x1D
        "urn:epc:id:", // 0x1E
        "urn:epc:tag:", // 0x1F
        "urn:epc:pat:", // 0x20
        "urn:epc:raw:", // 0x21
        "urn:epc:", // 0x22
        "urn:nfc:", // 0x23
    };

    private final short mTnf;
    private final byte[] mType;
    private final byte[] mId;
    private final byte[] mPayload;

    public static NdefRecord createApplicationRecord(String packageName) {
        if (packageName == null) {
            throw new NullPointerException("packageName is null");
        }
        if (packageName.isEmpty()) {
            throw new IllegalArgumentException("packageName is empty");
        }
        return new NdefRecord(
                TNF_EXTERNAL_TYPE,
                RTD_ANDROID_APP,
                null,
                packageName.getBytes(StandardCharsets.UTF_8));
    }

    public static NdefRecord createUri(Uri uri) {
        if (uri == null) {
            throw new NullPointerException("uri is null");
        }
        uri = uri.normalizeScheme();
        String uriString = uri.toString();
        if (uriString.isEmpty()) {
            throw new IllegalArgumentException("uri is empty");
        }
        byte prefix = 0;
        for (int i = 1; i < URI_PREFIX_MAP.length; i++) {
            if (uriString.startsWith(URI_PREFIX_MAP[i])) {
                prefix = (byte) i;
                uriString = uriString.substring(URI_PREFIX_MAP[i].length());
                break;
            }
        }
        byte[] uriBytes = uriString.getBytes(StandardCharsets.UTF_8);
        byte[] recordBytes = new byte[uriBytes.length + 1];
        recordBytes[0] = prefix;
        System.arraycopy(uriBytes, 0, recordBytes, 1, uriBytes.length);
        return new NdefRecord(TNF_WELL_KNOWN, RTD_URI, null, recordBytes);
    }

    public static NdefRecord createUri(String uriString) {
        if (uriString == null) {
            throw new NullPointerException("uriString is null");
        }
        return createUri(Uri.parse(uriString));
    }

    public static NdefRecord createMime(String mimeType, byte[] mimeData) {
        if (mimeType == null) {
            throw new NullPointerException("mimeType is null");
        }
        mimeType = normalizeMimeType(mimeType);
        if (mimeType.isEmpty()) {
            throw new IllegalArgumentException("mimeType is empty");
        }
        int slashIndex = mimeType.indexOf('/');
        if (slashIndex == 0) {
            throw new IllegalArgumentException("mimeType must have major type");
        }
        if (slashIndex == mimeType.length() - 1) {
            throw new IllegalArgumentException("mimeType must have minor type");
        }
        byte[] typeBytes = mimeType.getBytes(StandardCharsets.US_ASCII);
        return new NdefRecord(TNF_MIME_MEDIA, typeBytes, null, mimeData);
    }

    public static NdefRecord createExternal(String domain, String type, byte[] data) {
        if (domain == null) {
            throw new NullPointerException("domain is null");
        }
        if (type == null) {
            throw new NullPointerException("type is null");
        }
        domain = domain.trim().toLowerCase(Locale.ROOT);
        type = type.trim().toLowerCase(Locale.ROOT);
        if (domain.isEmpty()) {
            throw new IllegalArgumentException("domain is empty");
        }
        if (type.isEmpty()) {
            throw new IllegalArgumentException("type is empty");
        }
        byte[] byteDomain = domain.getBytes(StandardCharsets.UTF_8);
        byte[] byteType = type.getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[byteDomain.length + 1 + byteType.length];
        System.arraycopy(byteDomain, 0, b, 0, byteDomain.length);
        b[byteDomain.length] = ':';
        System.arraycopy(byteType, 0, b, byteDomain.length + 1, byteType.length);
        return new NdefRecord(TNF_EXTERNAL_TYPE, b, null, data);
    }

    public static NdefRecord createTextRecord(String languageCode, String text) {
        if (text == null) {
            throw new NullPointerException("text is null");
        }
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] languageCodeBytes;
        if (languageCode != null && !languageCode.isEmpty()) {
            languageCodeBytes = languageCode.getBytes(StandardCharsets.US_ASCII);
        } else {
            languageCodeBytes = Locale.getDefault().getLanguage()
                    .getBytes(StandardCharsets.US_ASCII);
        }
        if (languageCodeBytes.length >= 64) {
            throw new IllegalArgumentException("language code is too long, must be <64 bytes.");
        }
        ByteBuffer buffer = ByteBuffer.allocate(1 + languageCodeBytes.length + textBytes.length);
        byte status = (byte) (languageCodeBytes.length & 0x3F);
        buffer.put(status);
        buffer.put(languageCodeBytes);
        buffer.put(textBytes);
        return new NdefRecord(TNF_WELL_KNOWN, RTD_TEXT, null, buffer.array());
    }

    private static String normalizeMimeType(String type) {
        if (type == null) {
            return null;
        }
        type = type.trim().toLowerCase(Locale.ROOT);
        final int semicolonIndex = type.indexOf(';');
        if (semicolonIndex != -1) {
            type = type.substring(0, semicolonIndex).trim();
        }
        return type;
    }

    public NdefRecord(short tnf, byte[] type, byte[] id, byte[] payload) {
        byte[] safeType = type != null ? type.clone() : EMPTY_BYTE_ARRAY;
        byte[] safeId = id != null ? id.clone() : EMPTY_BYTE_ARRAY;
        byte[] safePayload = payload != null ? payload.clone() : EMPTY_BYTE_ARRAY;

        String message = validateTnf(tnf, safeType, safeId, safePayload);
        if (message != null) {
            throw new IllegalArgumentException(message);
        }
        if (safeType.length > 255) {
            throw new IllegalArgumentException("type length must be <= 255");
        }
        if (safeId.length > 255) {
            throw new IllegalArgumentException("id length must be <= 255");
        }

        mTnf = tnf;
        mType = safeType;
        mId = safeId;
        mPayload = safePayload;
    }

    @Deprecated
    public NdefRecord(byte[] data) throws FormatException {
        if (data == null) {
            throw new NullPointerException("data is null");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        NdefRecord[] rs = parse(buffer, true);
        if (buffer.remaining() > 0) {
            throw new FormatException("data too long");
        }
        mTnf = rs[0].mTnf;
        mType = rs[0].mType;
        mId = rs[0].mId;
        mPayload = rs[0].mPayload;
    }

    public short getTnf() {
        return mTnf;
    }

    public byte[] getType() {
        return mType.clone();
    }

    public byte[] getId() {
        return mId.clone();
    }

    public byte[] getPayload() {
        return mPayload.clone();
    }

    @Deprecated
    public byte[] toByteArray() {
        ByteBuffer data = ByteBuffer.allocate(getByteLength());
        writeToByteBuffer(data, true, true);
        return data.array();
    }

    public String toMimeType() {
        switch (mTnf) {
            case TNF_WELL_KNOWN:
                if (Arrays.equals(mType, RTD_TEXT)) {
                    return "text/plain";
                }
                break;
            case TNF_MIME_MEDIA:
                String mimeType = new String(mType, StandardCharsets.US_ASCII);
                return normalizeMimeType(mimeType);
        }
        return null;
    }

    public Uri toUri() {
        return toUri(false);
    }

    private Uri toUri(boolean inSmartPoster) {
        switch (mTnf) {
            case TNF_WELL_KNOWN:
                if (Arrays.equals(mType, RTD_SMART_POSTER) && !inSmartPoster) {
                    try {
                        NdefMessage nestedMessage = new NdefMessage(mPayload);
                        NdefRecord[] records = nestedMessage.getRecords();
                        for (int i = 0; i < records.length; i++) {
                            Uri uri = records[i].toUri(true);
                            if (uri != null) {
                                return uri;
                            }
                        }
                    } catch (FormatException ignored) {
                    }
                } else if (Arrays.equals(mType, RTD_URI)) {
                    Uri wktUri = parseWktUri();
                    return wktUri != null ? wktUri.normalizeScheme() : null;
                }
                break;

            case TNF_ABSOLUTE_URI:
                Uri uri = Uri.parse(new String(mType, StandardCharsets.UTF_8));
                return uri.normalizeScheme();

            case TNF_EXTERNAL_TYPE:
                if (!inSmartPoster) {
                    return Uri.parse("vnd.android.nfc://ext/"
                            + new String(mType, StandardCharsets.US_ASCII));
                }
                break;
        }
        return null;
    }

    private Uri parseWktUri() {
        if (mPayload.length < 2) {
            return null;
        }
        int prefixIndex = mPayload[0] & 0xFF;
        if (prefixIndex < 0 || prefixIndex >= URI_PREFIX_MAP.length) {
            return null;
        }
        String prefix = URI_PREFIX_MAP[prefixIndex];
        String suffix = new String(
                Arrays.copyOfRange(mPayload, 1, mPayload.length),
                StandardCharsets.UTF_8);
        return Uri.parse(prefix + suffix);
    }

    static NdefRecord[] parse(ByteBuffer buffer, boolean ignoreMbMe) throws FormatException {
        ArrayList<NdefRecord> records = new ArrayList<NdefRecord>();
        byte[] type = null;
        byte[] id = null;
        byte[] payload = null;
        ArrayList<byte[]> chunks = new ArrayList<byte[]>();
        boolean inChunk = false;
        short chunkTnf = -1;
        boolean me = false;

        while (!me) {
            byte flag;
            try {
                flag = buffer.get();
            } catch (BufferUnderflowException e) {
                throw new FormatException("expected more data", e);
            }

            boolean mb = (flag & FLAG_MB) != 0;
            me = (flag & FLAG_ME) != 0;
            boolean cf = (flag & FLAG_CF) != 0;
            boolean sr = (flag & FLAG_SR) != 0;
            boolean il = (flag & FLAG_IL) != 0;
            short tnf = (short) (flag & 0x07);

            if (!mb && records.isEmpty() && !inChunk && !ignoreMbMe) {
                throw new FormatException("expected MB flag");
            } else if (mb && (!records.isEmpty() || inChunk) && !ignoreMbMe) {
                throw new FormatException("unexpected MB flag");
            } else if (inChunk && il) {
                throw new FormatException("unexpected IL flag in non-leading chunk");
            } else if (cf && me) {
                throw new FormatException("unexpected ME flag in non-trailing chunk");
            } else if (inChunk && tnf != TNF_UNCHANGED) {
                throw new FormatException("expected TNF_UNCHANGED in non-leading chunk");
            } else if (!inChunk && tnf == TNF_UNCHANGED) {
                throw new FormatException(
                        "unexpected TNF_UNCHANGED in first chunk or unchunked record");
            }

            int typeLength;
            long payloadLength;
            int idLength;
            try {
                typeLength = buffer.get() & 0xFF;
                payloadLength = sr ? (buffer.get() & 0xFF) : (buffer.getInt() & 0xFFFFFFFFL);
                idLength = il ? (buffer.get() & 0xFF) : 0;
            } catch (BufferUnderflowException e) {
                throw new FormatException("expected more data", e);
            }

            if (inChunk && typeLength != 0) {
                throw new FormatException("expected zero-length type in non-leading chunk");
            }

            if (!inChunk) {
                type = typeLength > 0 ? new byte[typeLength] : EMPTY_BYTE_ARRAY;
                id = idLength > 0 ? new byte[idLength] : EMPTY_BYTE_ARRAY;
                try {
                    buffer.get(type);
                    buffer.get(id);
                } catch (BufferUnderflowException e) {
                    throw new FormatException("expected more data", e);
                }
            }

            ensureSanePayloadSize(payloadLength);
            payload = payloadLength > 0 ? new byte[(int) payloadLength] : EMPTY_BYTE_ARRAY;
            try {
                buffer.get(payload);
            } catch (BufferUnderflowException e) {
                throw new FormatException("expected more data", e);
            }

            if (cf && !inChunk) {
                if (typeLength == 0 && tnf != TNF_UNKNOWN) {
                    throw new FormatException("expected non-zero type length in first chunk");
                }
                chunks.clear();
                chunkTnf = tnf;
            }
            if (cf || inChunk) {
                chunks.add(payload);
            }
            if (!cf && inChunk) {
                long totalLength = 0;
                for (int i = 0; i < chunks.size(); i++) {
                    totalLength += chunks.get(i).length;
                }
                ensureSanePayloadSize(totalLength);
                payload = new byte[(int) totalLength];
                int offset = 0;
                for (int i = 0; i < chunks.size(); i++) {
                    byte[] p = chunks.get(i);
                    System.arraycopy(p, 0, payload, offset, p.length);
                    offset += p.length;
                }
                tnf = chunkTnf;
            }
            if (cf) {
                inChunk = true;
                continue;
            } else {
                inChunk = false;
            }

            String error = validateTnf(tnf, type, id, payload);
            if (error != null) {
                throw new FormatException(error);
            }
            records.add(new NdefRecord(tnf, type, id, payload));
            if (ignoreMbMe) {
                break;
            }
        }
        return records.toArray(new NdefRecord[records.size()]);
    }

    private static void ensureSanePayloadSize(long size) throws FormatException {
        if (size > MAX_PAYLOAD_SIZE) {
            throw new FormatException(
                    "payload above max limit: " + size + " > " + MAX_PAYLOAD_SIZE);
        }
    }

    static String validateTnf(short tnf, byte[] type, byte[] id, byte[] payload) {
        switch (tnf) {
            case TNF_EMPTY:
                if (type.length != 0 || id.length != 0 || payload.length != 0) {
                    return "unexpected data in TNF_EMPTY record";
                }
                return null;
            case TNF_WELL_KNOWN:
            case TNF_MIME_MEDIA:
            case TNF_ABSOLUTE_URI:
            case TNF_EXTERNAL_TYPE:
                return null;
            case TNF_UNKNOWN:
                if (type.length != 0) {
                    return "unexpected type field in TNF_UNKNOWN record";
                }
                return null;
            case TNF_UNCHANGED:
                return "unexpected TNF_UNCHANGED in first chunk or logical record";
            default:
                return String.format(Locale.US, "unexpected tnf value: 0x%02x",
                        Short.valueOf(tnf));
        }
    }

    void writeToByteBuffer(ByteBuffer buffer, boolean mb, boolean me) {
        boolean sr = mPayload.length < 256;
        boolean il = mTnf == TNF_EMPTY ? true : mId.length > 0;
        if (mTnf == TNF_EMPTY) {
            il = false;
        }

        byte flags = (byte) ((mb ? FLAG_MB : 0)
                | (me ? FLAG_ME : 0)
                | (sr ? FLAG_SR : 0)
                | (il ? FLAG_IL : 0)
                | (mTnf & 0x07));
        buffer.put(flags);

        buffer.put((byte) mType.length);
        if (sr) {
            buffer.put((byte) mPayload.length);
        } else {
            buffer.putInt(mPayload.length);
        }
        if (il) {
            buffer.put((byte) mId.length);
        }

        buffer.put(mType);
        if (il) {
            buffer.put(mId);
        }
        buffer.put(mPayload);
    }

    int getByteLength() {
        int length = 3 + mType.length + mId.length + mPayload.length;
        boolean sr = mPayload.length < 256;
        boolean il = mTnf != TNF_EMPTY && mId.length > 0;
        if (!sr) {
            length += 3;
        }
        if (il) {
            length += 1;
        }
        return length;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTnf);
        dest.writeInt(mType.length);
        dest.writeByteArray(mType);
        dest.writeInt(mId.length);
        dest.writeByteArray(mId);
        dest.writeInt(mPayload.length);
        dest.writeByteArray(mPayload);
    }

    public static final Parcelable.Creator<NdefRecord> CREATOR =
            new Parcelable.Creator<NdefRecord>() {
                @Override
                public NdefRecord createFromParcel(Parcel in) {
                    short tnf = (short) in.readInt();
                    int typeLength = in.readInt();
                    byte[] type = in.createByteArray();
                    int idLength = in.readInt();
                    byte[] id = in.createByteArray();
                    int payloadLength = in.readInt();
                    byte[] payload = in.createByteArray();
                    return new NdefRecord(
                            tnf,
                            type != null ? type : new byte[Math.max(0, typeLength)],
                            id != null ? id : new byte[Math.max(0, idLength)],
                            payload != null ? payload : new byte[Math.max(0, payloadLength)]);
                }

                @Override
                public NdefRecord[] newArray(int size) {
                    return new NdefRecord[size];
                }
            };

    @Override
    public int hashCode() {
        int result = 31 + Arrays.hashCode(mId);
        result = 31 * result + Arrays.hashCode(mPayload);
        result = 31 * result + mTnf;
        result = 31 * result + Arrays.hashCode(mType);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NdefRecord)) {
            return false;
        }
        NdefRecord other = (NdefRecord) obj;
        return mTnf == other.mTnf
                && Arrays.equals(mType, other.mType)
                && Arrays.equals(mId, other.mId)
                && Arrays.equals(mPayload, other.mPayload);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(
                String.format(Locale.US, "NdefRecord tnf=%X", Short.valueOf(mTnf)));
        if (mType.length > 0) {
            b.append(" type=").append(bytesToHex(mType));
        }
        if (mId.length > 0) {
            b.append(" id=").append(bytesToHex(mId));
        }
        if (mPayload.length > 0) {
            b.append(" payload=").append(bytesToHex(mPayload));
        }
        return b.toString();
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format(Locale.US, "%02X", Integer.valueOf(data[i] & 0xFF)));
        }
        return sb.toString();
    }
}
