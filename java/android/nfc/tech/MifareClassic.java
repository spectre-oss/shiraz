// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.Tag;
import java.io.IOException;

public final class MifareClassic extends BasicTagTechnology {
    public static final byte[] KEY_DEFAULT = {
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
    };
    public static final byte[] KEY_MIFARE_APPLICATION_DIRECTORY = {
        (byte) 0xA0, (byte) 0xA1, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5
    };
    public static final byte[] KEY_NFC_FORUM = {
        (byte) 0xD3, (byte) 0xF7, (byte) 0xD3, (byte) 0xF7, (byte) 0xD3, (byte) 0xF7
    };

    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_CLASSIC = 0;
    public static final int TYPE_PLUS = 1;
    public static final int TYPE_PRO = 2;

    public static final int SIZE_1K = 1024;
    public static final int SIZE_2K = 2048;
    public static final int SIZE_4K = 4096;
    public static final int SIZE_MINI = 320;

    public static final int BLOCK_SIZE = 16;

    private static final int MAX_BLOCK_COUNT = 256;
    private static final int MAX_SECTOR_COUNT = 40;

    private final int mType;
    private final int mSize;
    private int mTimeout = 618;

    private MifareClassic(Tag tag) {
        super(tag, Tag.TECH_MIFARE_CLASSIC);
        NfcA a = NfcA.get(tag);
        short sak = a != null ? a.getSak() : 0x08;
        switch (sak) {
            case 0x01:
            case 0x08:
            case 0x28:
            case 0x88:
                mType = TYPE_CLASSIC;
                mSize = SIZE_1K;
                break;
            case 0x09:
                mType = TYPE_CLASSIC;
                mSize = SIZE_MINI;
                break;
            case 0x10:
                mType = TYPE_PLUS;
                mSize = SIZE_2K;
                break;
            case 0x11:
                mType = TYPE_PLUS;
                mSize = SIZE_4K;
                break;
            case 0x18:
            case 0x38:
                mType = TYPE_CLASSIC;
                mSize = SIZE_4K;
                break;
            case 0x19:
                mType = TYPE_CLASSIC;
                mSize = SIZE_2K;
                break;
            case 0x98:
            case 0xB8:
                mType = TYPE_PRO;
                mSize = SIZE_4K;
                break;
            default:
                mType = TYPE_CLASSIC;
                mSize = SIZE_1K;
                break;
        }
    }

    public static MifareClassic get(Tag tag) {
        if (tag == null || !tag.hasTech(Tag.TECH_MIFARE_CLASSIC)) {
            return null;
        }
        return new MifareClassic(tag);
    }

    public int getType() {
        return mType;
    }

    public int getSize() {
        return mSize;
    }

    public int getSectorCount() {
        switch (mSize) {
            case SIZE_1K:
                return 16;
            case SIZE_2K:
                return 32;
            case SIZE_4K:
                return 40;
            case SIZE_MINI:
                return 5;
            default:
                return 0;
        }
    }

    public int getBlockCount() {
        return mSize / BLOCK_SIZE;
    }

    public int getBlockCountInSector(int sectorIndex) {
        validateSector(sectorIndex);
        if (sectorIndex < 32) {
            return 4;
        }
        return 16;
    }

    public int blockToSector(int blockIndex) {
        validateBlock(blockIndex);
        if (blockIndex < 32 * 4) {
            return blockIndex / 4;
        }
        return 32 + (blockIndex - 32 * 4) / 16;
    }

    public int sectorToBlock(int sectorIndex) {
        validateSector(sectorIndex);
        if (sectorIndex < 32) {
            return sectorIndex * 4;
        }
        return 32 * 4 + (sectorIndex - 32) * 16;
    }

    private static void validateSector(int sector) {
        if (sector < 0 || sector >= MAX_SECTOR_COUNT) {
            throw new IndexOutOfBoundsException("sector out of bounds: " + sector);
        }
    }

    private static void validateBlock(int block) {
        if (block < 0 || block >= MAX_BLOCK_COUNT) {
            throw new IndexOutOfBoundsException("block out of bounds: " + block);
        }
    }

    private static void validateValueOperand(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value cannot be negative");
        }
    }

    private static void validateKey(byte[] key) {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
        if (key.length != 6) {
            throw new IllegalArgumentException("key must be 6 bytes");
        }
    }

    public boolean authenticateSectorWithKeyA(int sectorIndex, byte[] key) throws IOException {
        validateSector(sectorIndex);
        validateKey(key);
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public boolean authenticateSectorWithKeyB(int sectorIndex, byte[] key) throws IOException {
        validateSector(sectorIndex);
        validateKey(key);
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public byte[] readBlock(int blockIndex) throws IOException {
        validateBlock(blockIndex);
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public void writeBlock(int blockIndex, byte[] data) throws IOException {
        validateBlock(blockIndex);
        if (data == null) {
            throw new NullPointerException("data cannot be null");
        }
        if (data.length != BLOCK_SIZE) {
            throw new IllegalArgumentException("must write 16-bytes");
        }
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public void increment(int blockIndex, int value) throws IOException {
        validateBlock(blockIndex);
        validateValueOperand(value);
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public void decrement(int blockIndex, int value) throws IOException {
        validateBlock(blockIndex);
        validateValueOperand(value);
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public void transfer(int blockIndex) throws IOException {
        validateBlock(blockIndex);
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public void restore(int blockIndex) throws IOException {
        validateBlock(blockIndex);
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public byte[] transceive(byte[] data) throws IOException {
        return transceiveInternal(data);
    }

    public int getMaxTransceiveLength() {
        return 253;
    }

    public void setTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }
        mTimeout = timeout;
    }

    public int getTimeout() {
        return mTimeout;
    }

    @Override
    public boolean isConnected() {
        return super.isConnected();
    }

    @Override
    public Tag getTag() {
        return super.getTag();
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    @Override
    public void connect() throws IOException {
        super.connect();
    }
}
