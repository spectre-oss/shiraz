// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** A Short Message Service message with 3GPP GSM 03.40 PDU parser and length calculator. */
public class SmsMessage {
    public static final int ENCODING_UNKNOWN = 0;
    public static final int ENCODING_7BIT = 1;
    public static final int ENCODING_8BIT = 2;
    public static final int ENCODING_16BIT = 3;
    public static final int ENCODING_KSC5601 = 4;

    public static final String FORMAT_3GPP = "3gpp";
    public static final String FORMAT_3GPP2 = "3gpp2";

    public static final int MAX_USER_DATA_BYTES = 140;
    public static final int MAX_USER_DATA_BYTES_WITH_HEADER = 134;
    public static final int MAX_USER_DATA_SEPTETS = 160;
    public static final int MAX_USER_DATA_SEPTETS_WITH_HEADER = 153;

    public enum MessageClass {
        UNKNOWN,
        CLASS_0,
        CLASS_1,
        CLASS_2,
        CLASS_3
    }

    public static class SubmitPdu {
        public byte[] encodedScAddress;
        public byte[] encodedMessage;

        public SubmitPdu() {
        }

        @Override
        public String toString() {
            return "SubmitPdu: encodedScAddress = " + Arrays.toString(encodedScAddress)
                    + ", encodedMessage = " + Arrays.toString(encodedMessage);
        }
    }

    private final byte[] mPdu;
    private String mScAddress;
    private String mOriginatingAddress;
    private String mMessageBody;
    private byte[] mUserData = new byte[0];
    private long mTimestampMillis;
    private int mProtocolIdentifier;
    private int mDataCodingScheme;
    private boolean mReplyPathPresent;
    private boolean mStatusReportMessage;
    private int mStatus;
    private MessageClass mMessageClass = MessageClass.UNKNOWN;

    private SmsMessage(byte[] pdu) {
        mPdu = pdu.clone();
    }

    @Deprecated
    public static SmsMessage createFromPdu(byte[] pdu) {
        return createFromPdu(pdu, FORMAT_3GPP);
    }

    public static SmsMessage createFromPdu(byte[] pdu, String format) {
        if (pdu == null || pdu.length < 2) {
            return null;
        }
        SmsMessage msg = new SmsMessage(pdu);
        msg.parsePdu(pdu, format);
        return msg;
    }

    private void parsePdu(byte[] pdu, String format) {
        try {
            int pos = 0;
            int scLen = pdu[pos++] & 0xFF;
            if (scLen > 0 && pos + scLen <= pdu.length) {
                mScAddress = decodeBcdAddress(pdu, pos, scLen);
                pos += scLen;
            }
            if (pos >= pdu.length) return;
            int firstOctet = pdu[pos++] & 0xFF;
            mReplyPathPresent = (firstOctet & 0x80) != 0;
            int mti = firstOctet & 0x03;
            mStatusReportMessage = (mti == 2);
            if (pos >= pdu.length) return;
            int addrDigits = pdu[pos++] & 0xFF;
            int addrBytes = 1 + ((addrDigits + 1) / 2);
            if (pos + addrBytes <= pdu.length) {
                mOriginatingAddress = decodeBcdDigits(pdu, pos, addrDigits);
                pos += addrBytes;
            }
            if (pos < pdu.length) mProtocolIdentifier = pdu[pos++] & 0xFF;
            if (pos < pdu.length) mDataCodingScheme = pdu[pos++] & 0xFF;
            if ((mDataCodingScheme & 0x10) != 0) {
                switch (mDataCodingScheme & 0x03) {
                    case 0: mMessageClass = MessageClass.CLASS_0; break;
                    case 1: mMessageClass = MessageClass.CLASS_1; break;
                    case 2: mMessageClass = MessageClass.CLASS_2; break;
                    case 3: mMessageClass = MessageClass.CLASS_3; break;
                }
            }
            if (pos + 7 <= pdu.length) {
                pos += 7; // SCTS
            }
            if (pos < pdu.length) {
                int udl = pdu[pos++] & 0xFF;
                int rem = pdu.length - pos;
                mUserData = Arrays.copyOfRange(pdu, pos, pdu.length);
                int coding = (mDataCodingScheme >> 2) & 0x03;
                if (coding == 2) {
                    int byteLen = Math.min(udl, rem);
                    mMessageBody = new String(pdu, pos, byteLen, StandardCharsets.UTF_16BE);
                } else if (coding == 1) {
                    int byteLen = Math.min(udl, rem);
                    mMessageBody = new String(pdu, pos, byteLen, StandardCharsets.ISO_8859_1);
                } else {
                    mMessageBody = unpackGsm7Bit(pdu, pos, udl);
                }
            }
        } catch (RuntimeException ignored) {
            if (mMessageBody == null) mMessageBody = "";
        }
    }

    private static String decodeBcdAddress(byte[] pdu, int offset, int byteCount) {
        if (byteCount <= 1) return "";
        int ton = pdu[offset] & 0xFF;
        String prefix = ((ton & 0x70) == 0x10) ? "+" : "";
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 1; i < byteCount; i++) {
            int b = pdu[offset + i] & 0xFF;
            int lo = b & 0x0F;
            int hi = (b >> 4) & 0x0F;
            if (lo < 10) sb.append((char) ('0' + lo));
            if (hi < 10) sb.append((char) ('0' + hi));
        }
        return sb.toString();
    }

    private static String decodeBcdDigits(byte[] pdu, int offset, int digits) {
        int ton = pdu[offset] & 0xFF;
        String prefix = ((ton & 0x70) == 0x10) ? "+" : "";
        StringBuilder sb = new StringBuilder(prefix);
        int read = 0;
        for (int i = 1; read < digits && offset + i < pdu.length; i++) {
            int b = pdu[offset + i] & 0xFF;
            int lo = b & 0x0F;
            if (read < digits && lo < 10) {
                sb.append((char) ('0' + lo));
                read++;
            }
            int hi = (b >> 4) & 0x0F;
            if (read < digits && hi < 10) {
                sb.append((char) ('0' + hi));
                read++;
            }
        }
        return sb.toString();
    }

    private static String unpackGsm7Bit(byte[] pdu, int offset, int septetCount) {
        StringBuilder sb = new StringBuilder(septetCount);
        for (int i = 0; i < septetCount; i++) {
            int bitOffset = i * 7;
            int byteIndex = offset + (bitOffset / 8);
            int shift = bitOffset % 8;
            if (byteIndex >= pdu.length) break;
            int val = (pdu[byteIndex] & 0xFF) >>> shift;
            if (shift > 1 && byteIndex + 1 < pdu.length) {
                val |= (pdu[byteIndex + 1] & 0xFF) << (8 - shift);
            }
            int septet = val & 0x7F;
            sb.append((char) (septet == 0 ? '@' : septet));
        }
        return sb.toString();
    }

    public static int getTPLayerLengthForPDU(String pdu) {
        if (pdu == null || pdu.length() < 2) return 0;
        int len = pdu.length() / 2;
        int cscaLen = Integer.parseInt(pdu.substring(0, 2), 16);
        return len - cscaLen - 1;
    }

    public static int[] calculateLength(CharSequence msgBody, boolean use7bitOnly) {
        if (msgBody == null || msgBody.length() == 0) {
            return new int[]{0, 0, MAX_USER_DATA_SEPTETS, ENCODING_7BIT, 0, 0};
        }
        boolean ascii7 = true;
        if (!use7bitOnly) {
            for (int i = 0; i < msgBody.length(); i++) {
                if (msgBody.charAt(i) > 0x7F) {
                    ascii7 = false;
                    break;
                }
            }
        }
        if (ascii7) {
            int septets = msgBody.length();
            int msgCount = septets <= MAX_USER_DATA_SEPTETS
                    ? 1
                    : (septets + MAX_USER_DATA_SEPTETS_WITH_HEADER - 1) / MAX_USER_DATA_SEPTETS_WITH_HEADER;
            int remaining = msgCount == 1
                    ? (MAX_USER_DATA_SEPTETS - septets)
                    : (msgCount * MAX_USER_DATA_SEPTETS_WITH_HEADER - septets);
            return new int[]{msgCount, septets, remaining, ENCODING_7BIT, 0, 0};
        } else {
            int units = msgBody.length();
            int maxSingle = MAX_USER_DATA_BYTES / 2;
            int maxMulti = MAX_USER_DATA_BYTES_WITH_HEADER / 2;
            int msgCount = units <= maxSingle ? 1 : (units + maxMulti - 1) / maxMulti;
            int remaining = msgCount == 1 ? (maxSingle - units) : (msgCount * maxMulti - units);
            return new int[]{msgCount, units, remaining, ENCODING_16BIT, 0, 0};
        }
    }

    public static int[] calculateLength(String messageBody, boolean use7bitOnly) {
        return calculateLength((CharSequence) messageBody, use7bitOnly);
    }

    public static SubmitPdu getSubmitPdu(
            String scAddress, String destinationAddress, String message, boolean statusReportRequested) {
        if (message == null || destinationAddress == null) return null;
        byte[] utf16 = message.getBytes(StandardCharsets.UTF_16BE);
        SubmitPdu pdu = new SubmitPdu();
        pdu.encodedScAddress = scAddress != null ? scAddress.getBytes(StandardCharsets.UTF_8) : null;
        byte[] msg = new byte[4 + utf16.length];
        msg[0] = (byte) (0x01 | (statusReportRequested ? 0x20 : 0x00));
        msg[1] = 0x00;
        msg[2] = 0x08; // UCS-2
        msg[3] = (byte) utf16.length;
        System.arraycopy(utf16, 0, msg, 4, utf16.length);
        pdu.encodedMessage = msg;
        return pdu;
    }

    public static SubmitPdu getSubmitPdu(
            String scAddress,
            String destinationAddress,
            short destinationPort,
            byte[] data,
            boolean statusReportRequested) {
        if (data == null || destinationAddress == null) return null;
        SubmitPdu pdu = new SubmitPdu();
        pdu.encodedScAddress = scAddress != null ? scAddress.getBytes(StandardCharsets.UTF_8) : null;
        byte[] msg = new byte[6 + data.length];
        msg[0] = (byte) (0x41 | (statusReportRequested ? 0x20 : 0x00));
        msg[1] = 0x00;
        msg[2] = 0x04; // 8-bit data
        msg[3] = (byte) data.length;
        msg[4] = (byte) ((destinationPort >> 8) & 0xFF);
        msg[5] = (byte) (destinationPort & 0xFF);
        System.arraycopy(data, 0, msg, 6, data.length);
        pdu.encodedMessage = msg;
        return pdu;
    }

    public String getServiceCenterAddress() { return mScAddress; }
    public String getOriginatingAddress() { return mOriginatingAddress; }
    public String getDisplayOriginatingAddress() { return mOriginatingAddress; }
    public String getMessageBody() { return mMessageBody; }
    public MessageClass getMessageClass() { return mMessageClass; }
    public String getDisplayMessageBody() { return mMessageBody; }
    public String getPseudoSubject() { return ""; }
    public long getTimestampMillis() { return mTimestampMillis; }
    public boolean isEmail() { return false; }
    public String getEmailBody() { return null; }
    public String getEmailFrom() { return null; }
    public int getProtocolIdentifier() { return mProtocolIdentifier; }
    public boolean isReplace() { return (mProtocolIdentifier & 0xC0) == 0x40 && (mProtocolIdentifier & 0x3F) > 0 && (mProtocolIdentifier & 0x3F) < 8; }
    public boolean isCphsMwiMessage() { return false; }
    public boolean isMWIClearMessage() { return (mDataCodingScheme & 0xF8) == 0xC0; }
    public boolean isMWISetMessage() { return (mDataCodingScheme & 0xF8) == 0xC8; }
    public boolean isMwiDontStore() { return (mDataCodingScheme & 0xF0) == 0xC0; }
    public byte[] getUserData() { return mUserData.clone(); }
    public byte[] getPdu() { return mPdu.clone(); }
    @Deprecated public int getStatusOnSim() { return -1; }
    public int getStatusOnIcc() { return -1; }
    @Deprecated public int getIndexOnSim() { return -1; }
    public int getIndexOnIcc() { return -1; }
    public int getStatus() { return mStatus; }
    public boolean isStatusReportMessage() { return mStatusReportMessage; }
    public boolean isReplyPathPresent() { return mReplyPathPresent; }
}
