// SPDX-License-Identifier: GPL-3.0-only
package android.net.rtp;

import java.net.InetAddress;
import java.net.SocketException;

public class AudioStream extends RtpStream {
    private AudioCodec mCodec;
    private int mDtmfType = -1;
    private AudioGroup mGroup;

    public AudioStream(InetAddress address) throws SocketException {
        super(address);
    }

    @Override
    public final boolean isBusy() {
        return mGroup != null;
    }

    public AudioGroup getGroup() {
        return mGroup;
    }

    public void join(AudioGroup group) {
        synchronized (this) {
            if (mGroup == group) {
                return;
            }
            if (mGroup != null) {
                mGroup.remove(this);
                mGroup = null;
            }
            if (group != null) {
                if (mCodec == null) {
                    throw new IllegalStateException("Codec is not set");
                }
                group.add(this);
                mGroup = group;
            }
        }
    }

    public AudioCodec getCodec() {
        return mCodec;
    }

    public void setCodec(AudioCodec codec) {
        if (isBusy()) {
            throw new IllegalStateException("Busy");
        }
        if (codec.type == mDtmfType) {
            throw new IllegalArgumentException("The type is used by DTMF");
        }
        this.mCodec = codec;
    }

    public int getDtmfType() {
        return mDtmfType;
    }

    public void setDtmfType(int type) {
        if (isBusy()) {
            throw new IllegalStateException("Busy");
        }
        if (type != -1) {
            if (type < 96 || type > 127) {
                throw new IllegalArgumentException("Invalid type");
            }
            if (mCodec != null && type == mCodec.type) {
                throw new IllegalArgumentException("The type is used by codec");
            }
        }
        this.mDtmfType = type;
    }
}
