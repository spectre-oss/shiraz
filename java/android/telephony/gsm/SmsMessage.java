// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.gsm;

import java.util.Arrays;

@Deprecated
public class SmsMessage {
    @Deprecated public static final int ENCODING_UNKNOWN = 0;
    @Deprecated public static final int ENCODING_7BIT = 1;
    @Deprecated public static final int ENCODING_8BIT = 2;
    @Deprecated public static final int ENCODING_16BIT = 3;

    @Deprecated public static final int MAX_USER_DATA_BYTES = 140;
    @Deprecated public static final int MAX_USER_DATA_SEPTETS = 160;
    @Deprecated public static final int MAX_USER_DATA_SEPTETS_WITH_HEADER = 153;

    @Deprecated
    public enum MessageClass {
        UNKNOWN,
        CLASS_0,
        CLASS_1,
        CLASS_2,
        CLASS_3
    }

    @Deprecated
    public static class SubmitPdu {
        @Deprecated public byte[] encodedScAddress;
        @Deprecated public byte[] encodedMessage;

        @Deprecated
        public SubmitPdu() {}

        public String toString() {
            return "SubmitPdu: encodedScAddress = " + Arrays.toString(encodedScAddress)
                    + ", encodedMessage = " + Arrays.toString(encodedMessage);
        }
    }

    private android.telephony.SmsMessage mWrappedMessage;

    @Deprecated
    public SmsMessage() {}

    private SmsMessage(android.telephony.SmsMessage wrapped) {
        mWrappedMessage = wrapped;
    }

    @Deprecated
    public static SmsMessage createFromPdu(byte[] pdu) {
        android.telephony.SmsMessage wrapped =
                android.telephony.SmsMessage.createFromPdu(pdu, android.telephony.SmsMessage.FORMAT_3GPP);
        return wrapped != null ? new SmsMessage(wrapped) : null;
    }

    @Deprecated
    public static int getTPLayerLengthForPDU(String pdu) {
        return android.telephony.SmsMessage.getTPLayerLengthForPDU(pdu);
    }

    @Deprecated
    public static int[] calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        int[] res = android.telephony.SmsMessage.calculateLength(messageBody, use7bitOnly);
        return new int[] { res[0], res[1], res[2], res[3] };
    }

    @Deprecated
    public static int[] calculateLength(String messageBody, boolean use7bitOnly) {
        return calculateLength((CharSequence) messageBody, use7bitOnly);
    }

    @Deprecated
    public static SubmitPdu getSubmitPdu(
            String scAddress, String destinationAddress, String message, boolean statusReportRequested) {
        android.telephony.SmsMessage.SubmitPdu w =
                android.telephony.SmsMessage.getSubmitPdu(
                        scAddress, destinationAddress, message, statusReportRequested);
        if (w == null) {
            return null;
        }
        SubmitPdu out = new SubmitPdu();
        out.encodedScAddress = w.encodedScAddress;
        out.encodedMessage = w.encodedMessage;
        return out;
    }

    @Deprecated
    public static SubmitPdu getSubmitPdu(
            String scAddress,
            String destinationAddress,
            short destinationPort,
            byte[] data,
            boolean statusReportRequested) {
        android.telephony.SmsMessage.SubmitPdu w =
                android.telephony.SmsMessage.getSubmitPdu(
                        scAddress, destinationAddress, destinationPort, data, statusReportRequested);
        if (w == null) {
            return null;
        }
        SubmitPdu out = new SubmitPdu();
        out.encodedScAddress = w.encodedScAddress;
        out.encodedMessage = w.encodedMessage;
        return out;
    }

    @Deprecated
    public String getServiceCenterAddress() {
        return mWrappedMessage != null ? mWrappedMessage.getServiceCenterAddress() : null;
    }

    @Deprecated
    public String getOriginatingAddress() {
        return mWrappedMessage != null ? mWrappedMessage.getOriginatingAddress() : null;
    }

    @Deprecated
    public String getDisplayOriginatingAddress() {
        return mWrappedMessage != null ? mWrappedMessage.getDisplayOriginatingAddress() : null;
    }

    @Deprecated
    public String getMessageBody() {
        return mWrappedMessage != null ? mWrappedMessage.getMessageBody() : null;
    }

    @Deprecated
    public MessageClass getMessageClass() {
        if (mWrappedMessage == null) {
            return MessageClass.UNKNOWN;
        }
        switch (mWrappedMessage.getMessageClass()) {
            case CLASS_0: return MessageClass.CLASS_0;
            case CLASS_1: return MessageClass.CLASS_1;
            case CLASS_2: return MessageClass.CLASS_2;
            case CLASS_3: return MessageClass.CLASS_3;
            default: return MessageClass.UNKNOWN;
        }
    }

    @Deprecated
    public String getDisplayMessageBody() {
        return mWrappedMessage != null ? mWrappedMessage.getDisplayMessageBody() : null;
    }

    @Deprecated
    public String getPseudoSubject() {
        return mWrappedMessage != null ? mWrappedMessage.getPseudoSubject() : "";
    }

    @Deprecated
    public long getTimestampMillis() {
        return mWrappedMessage != null ? mWrappedMessage.getTimestampMillis() : 0L;
    }

    @Deprecated
    public boolean isEmail() {
        return mWrappedMessage != null && mWrappedMessage.isEmail();
    }

    @Deprecated
    public String getEmailBody() {
        return mWrappedMessage != null ? mWrappedMessage.getEmailBody() : null;
    }

    @Deprecated
    public String getEmailFrom() {
        return mWrappedMessage != null ? mWrappedMessage.getEmailFrom() : null;
    }

    @Deprecated
    public int getProtocolIdentifier() {
        return mWrappedMessage != null ? mWrappedMessage.getProtocolIdentifier() : 0;
    }

    @Deprecated
    public boolean isReplace() {
        return mWrappedMessage != null && mWrappedMessage.isReplace();
    }

    @Deprecated
    public boolean isCphsMwiMessage() {
        return mWrappedMessage != null && mWrappedMessage.isCphsMwiMessage();
    }

    @Deprecated
    public boolean isMWIClearMessage() {
        return mWrappedMessage != null && mWrappedMessage.isMWIClearMessage();
    }

    @Deprecated
    public boolean isMWISetMessage() {
        return mWrappedMessage != null && mWrappedMessage.isMWISetMessage();
    }

    @Deprecated
    public boolean isMwiDontStore() {
        return mWrappedMessage != null && mWrappedMessage.isMwiDontStore();
    }

    @Deprecated
    public byte[] getUserData() {
        return mWrappedMessage != null ? mWrappedMessage.getUserData() : new byte[0];
    }

    @Deprecated
    public byte[] getPdu() {
        return mWrappedMessage != null ? mWrappedMessage.getPdu() : new byte[0];
    }

    @Deprecated
    public int getStatusOnSim() {
        return mWrappedMessage != null ? mWrappedMessage.getStatusOnIcc() : -1;
    }

    @Deprecated
    public int getIndexOnSim() {
        return mWrappedMessage != null ? mWrappedMessage.getIndexOnIcc() : -1;
    }

    @Deprecated
    public int getStatus() {
        return mWrappedMessage != null ? mWrappedMessage.getStatus() : 0;
    }

    @Deprecated
    public boolean isStatusReportMessage() {
        return mWrappedMessage != null && mWrappedMessage.isStatusReportMessage();
    }

    @Deprecated
    public boolean isReplyPathPresent() {
        return mWrappedMessage != null && mWrappedMessage.isReplyPathPresent();
    }
}
