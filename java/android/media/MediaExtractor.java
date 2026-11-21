// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.metrics.LogSessionId;
import android.net.Uri;
import android.os.PersistableBundle;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import shiraz.runtime.NativeFd;

public final class MediaExtractor {
    public static final int SAMPLE_FLAG_SYNC = 1;
    public static final int SAMPLE_FLAG_ENCRYPTED = 2;
    public static final int SAMPLE_FLAG_PARTIAL_FRAME = 4;

    public static final int SEEK_TO_PREVIOUS_SYNC = 0;
    public static final int SEEK_TO_NEXT_SYNC = 1;
    public static final int SEEK_TO_CLOSEST_SYNC = 2;

    public static final class CasInfo {
        private final int mSystemId;
        private final byte[] mPrivateData;
        private final MediaCas.Session mSession;

        CasInfo(int systemId, MediaCas.Session session, byte[] privateData) {
            mSystemId = systemId;
            mSession = session;
            mPrivateData = privateData != null ? privateData.clone() : null;
        }

        public int getSystemId() {
            return mSystemId;
        }

        public byte[] getPrivateData() {
            return mPrivateData != null ? mPrivateData.clone() : null;
        }

        public MediaCas.Session getSession() {
            return mSession;
        }
    }

    public static final class MetricsConstants {
        public static final String FORMAT = "android.media.mediaextractor.fmt";
        public static final String MIME_TYPE = "android.media.mediaextractor.mime";
        public static final String TRACKS = "android.media.mediaextractor.ntrk";

        private MetricsConstants() {
        }
    }

    private static final class SampleEntry {
        final int trackIndex;
        final int offset;
        final int size;
        final long timeUs;
        final int flags;

        SampleEntry(int trackIndex, int offset, int size, long timeUs, int flags) {
            this.trackIndex = trackIndex;
            this.offset = offset;
            this.size = size;
            this.timeUs = timeUs;
            this.flags = flags;
        }
    }

    private static final int MAX_CONTAINER_BYTES = 32 * 1024 * 1024;

    private byte[] mData;
    private String mContainerName = "unknown";
    private final List<MediaFormat> mTracks = new ArrayList<MediaFormat>();
    private final List<Boolean> mSelected = new ArrayList<Boolean>();
    private final List<SampleEntry> mAllSamples = new ArrayList<SampleEntry>();
    private final List<SampleEntry> mActiveSamples = new ArrayList<SampleEntry>();
    private final Map<UUID, byte[]> mPssh = new LinkedHashMap<UUID, byte[]>();
    private int mSampleCursor;
    private LogSessionId mLogSessionId = LogSessionId.LOG_SESSION_ID_NONE;
    private MediaCas mMediaCas;
    private boolean mReleased;

    public MediaExtractor() {
    }

    private void checkLive() {
        if (mReleased) {
            throw new IllegalStateException("MediaExtractor has been released");
        }
    }

    public void setDataSource(MediaDataSource dataSource) throws IOException {
        checkLive();
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        long size = dataSource.getSize();
        if (size < 0 || size > MAX_CONTAINER_BYTES) {
            throw new IOException("Unsupported MediaDataSource size: " + size);
        }
        byte[] bytes = new byte[(int) size];
        int done = 0;
        while (done < bytes.length) {
            int n = dataSource.readAt(done, bytes, done, bytes.length - done);
            if (n <= 0) {
                break;
            }
            done += n;
        }
        if (done < bytes.length) {
            byte[] trimmed = new byte[done];
            System.arraycopy(bytes, 0, trimmed, 0, done);
            bytes = trimmed;
        }
        parseContainer(bytes);
    }

    public void setDataSource(Context context, Uri uri, Map<String, String> headers) throws IOException {
        checkLive();
        if (context == null || uri == null) {
            throw new IllegalArgumentException("context and uri must not be null");
        }
        String scheme = uri.getScheme();
        if (scheme == null || "file".equals(scheme)) {
            setDataSource(uri.getPath());
            return;
        }
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                || "rtsp".equalsIgnoreCase(scheme)) {
            throw new IOException("Network media extraction is not supported: " + uri);
        }
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new FileNotFoundException(uri.toString());
            }
            parseContainer(readStream(in));
        }
    }

    public void setDataSource(String path, Map<String, String> headers) throws IOException {
        setDataSource(path);
    }

    public void setDataSource(String path) throws IOException {
        checkLive();
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("rtsp://")) {
            throw new IOException("Network media extraction is not supported: " + path);
        }
        String localPath = path.startsWith("file://") ? Uri.parse(path).getPath() : path;
        try (FileInputStream in = new FileInputStream(localPath)) {
            setDataSource(in.getFD());
        }
    }

    public void setDataSource(AssetFileDescriptor afd)
            throws IOException, IllegalArgumentException, IllegalStateException {
        checkLive();
        if (afd == null) {
            throw new IllegalArgumentException("afd must not be null");
        }
        if (afd.getDeclaredLength() < 0) {
            setDataSource(afd.getFileDescriptor());
        } else {
            setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());
        }
    }

    public void setDataSource(FileDescriptor fd) throws IOException {
        checkLive();
        if (fd == null) {
            throw new IllegalArgumentException("fd must not be null");
        }
        setDataSource(fd, 0L, NativeFd.size(fd));
    }

    public void setDataSource(FileDescriptor fd, long offset, long length) throws IOException {
        checkLive();
        if (fd == null || offset < 0 || length < 0 || length > MAX_CONTAINER_BYTES) {
            throw new IOException("Invalid MediaExtractor FileDescriptor region");
        }
        byte[] bytes;
        synchronized (fd) {
            bytes = NativeFd.readRegion(fd, offset, length);
        }
        parseContainer(bytes);
    }

    private static byte[] readStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > MAX_CONTAINER_BYTES) {
                throw new IOException("MediaExtractor stream exceeds limit");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private void parseContainer(byte[] data) throws IOException {
        mTracks.clear();
        mSelected.clear();
        mAllSamples.clear();
        mActiveSamples.clear();
        mPssh.clear();
        mSampleCursor = 0;
        mData = data;

        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E') {
            parseWav(data);
            return;
        }
        if (data.length >= 7 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xF6) == 0xF0) {
            parseAdts(data);
            return;
        }
        if (data.length >= 8 && isMp4TopBox(new String(data, 4, 4, StandardCharsets.US_ASCII))) {
            parseMp4(data);
            return;
        }
        throw new IOException("Unsupported or corrupted media container");
    }

    private static boolean isMp4TopBox(String type) {
        return "ftyp".equals(type) || "moov".equals(type) || "mdat".equals(type)
                || "free".equals(type) || "skip".equals(type) || "wide".equals(type);
    }

    private void parseWav(byte[] data) throws IOException {
        mContainerName = "wav";
        ByteBuffer le = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 16;
        int dataOffset = -1;
        int dataSize = 0;
        while (pos + 8 <= data.length) {
            String id = new String(data, pos, 4, StandardCharsets.US_ASCII);
            int chunkSize = le.getInt(pos + 4);
            if (chunkSize < 0 || pos + 8L + chunkSize > data.length) {
                break;
            }
            if ("fmt ".equals(id) && chunkSize >= 16) {
                channels = le.getShort(pos + 10) & 0xFFFF;
                sampleRate = le.getInt(pos + 12);
                bitsPerSample = le.getShort(pos + 22) & 0xFFFF;
            } else if ("data".equals(id)) {
                dataOffset = pos + 8;
                dataSize = chunkSize;
                break;
            }
            pos += 8 + chunkSize + (chunkSize & 1);
        }
        if (channels <= 0 || sampleRate <= 0 || dataOffset < 0) {
            throw new IOException("Invalid WAV header");
        }
        MediaFormat fmt = MediaFormat.createAudioFormat("audio/raw", sampleRate, channels);
        int frameBytes = Math.max(1, channels * Math.max(1, bitsPerSample / 8));
        long durationUs = ((long) (dataSize / frameBytes) * 1000000L) / sampleRate;
        fmt.setLong(MediaFormat.KEY_DURATION, durationUs);
        fmt.setInteger(MediaFormat.KEY_PCM_ENCODING,
                bitsPerSample == 8 ? AudioFormat.ENCODING_PCM_8BIT : AudioFormat.ENCODING_PCM_16BIT);
        mTracks.add(fmt);
        mSelected.add(Boolean.FALSE);

        int chunkFrames = 1024;
        int chunkBytes = chunkFrames * frameBytes;
        int cur = 0;
        long framesDone = 0;
        while (cur < dataSize) {
            int sz = Math.min(chunkBytes, dataSize - cur);
            long ptsUs = (framesDone * 1000000L) / sampleRate;
            mAllSamples.add(new SampleEntry(0, dataOffset + cur, sz, ptsUs, SAMPLE_FLAG_SYNC));
            cur += sz;
            framesDone += sz / frameBytes;
        }
    }

    private void parseAdts(byte[] data) throws IOException {
        mContainerName = "aac-adts";
        int[] rates = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000};
        int freqIdx = (data[2] >> 2) & 0x0F;
        if (freqIdx >= rates.length) {
            throw new IOException("Invalid ADTS sample rate index");
        }
        int sampleRate = rates[freqIdx];
        int channels = ((data[2] & 0x01) << 2) | ((data[3] >> 6) & 0x03);
        if (channels <= 0) {
            channels = 2;
        }
        MediaFormat fmt = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channels);
        int csdVal = (2 << 11) | (freqIdx << 7) | (channels << 3);
        fmt.setByteBuffer("csd-0", ByteBuffer.wrap(new byte[]{(byte) (csdVal >> 8), (byte) csdVal}));
        mTracks.add(fmt);
        mSelected.add(Boolean.FALSE);

        int pos = 0;
        long frameIdx = 0;
        while (pos + 7 <= data.length) {
            if ((data[pos] & 0xFF) != 0xFF || (data[pos + 1] & 0xF6) != 0xF0) {
                break;
            }
            int headerLen = ((data[pos + 1] & 0x01) == 0) ? 9 : 7;
            int frameLen = ((data[pos + 3] & 0x03) << 11)
                    | ((data[pos + 4] & 0xFF) << 3)
                    | ((data[pos + 5] >> 5) & 0x07);
            if (frameLen < headerLen || pos + frameLen > data.length) {
                break;
            }
            long ptsUs = (frameIdx * 1024L * 1000000L) / sampleRate;
            mAllSamples.add(new SampleEntry(0, pos + headerLen, frameLen - headerLen, ptsUs, SAMPLE_FLAG_SYNC));
            pos += frameLen;
            frameIdx++;
        }
        fmt.setLong(MediaFormat.KEY_DURATION, (frameIdx * 1024L * 1000000L) / sampleRate);
    }

    private void parseMp4(byte[] data) throws IOException {
        mContainerName = "mp4";
        ByteBuffer be = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        walkMp4Boxes(be, 0, data.length);
        if (mTracks.isEmpty()) {
            throw new IOException("No valid tracks found in MP4 container");
        }
        Collections.sort(mAllSamples, new java.util.Comparator<SampleEntry>() {
            @Override
            public int compare(SampleEntry a, SampleEntry b) {
                return Long.compare(a.timeUs, b.timeUs);
            }
        });
    }

    private void walkMp4Boxes(ByteBuffer be, int start, int end) {
        int pos = start;
        while (pos + 8 <= end) {
            long size = be.getInt(pos) & 0xFFFFFFFFL;
            byte[] t = new byte[4];
            for (int i = 0; i < 4; i++) {
                t[i] = be.get(pos + 4 + i);
            }
            String type = new String(t, StandardCharsets.US_ASCII);
            int header = 8;
            if (size == 1 && pos + 16 <= end) {
                size = be.getLong(pos + 8);
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header || pos + size > end) {
                break;
            }
            int boxEnd = (int) (pos + size);
            if ("moov".equals(type)) {
                walkMp4Boxes(be, pos + header, boxEnd);
            } else if ("pssh".equals(type) && boxEnd - (pos + header) >= 24) {
                int p = pos + header;
                int version = be.get(p) & 0xFF;
                long msb = be.getLong(p + 4);
                long lsb = be.getLong(p + 12);
                int q = p + 20;
                if (version > 0 && q + 4 <= boxEnd) {
                    int kidCount = be.getInt(q);
                    q += 4 + kidCount * 16;
                }
                if (q + 4 <= boxEnd) {
                    int dataLen = be.getInt(q);
                    if (dataLen >= 0 && q + 4 + dataLen <= boxEnd) {
                        byte[] psshData = new byte[dataLen];
                        for (int i = 0; i < dataLen; i++) {
                            psshData[i] = be.get(q + 4 + i);
                        }
                        mPssh.put(new UUID(msb, lsb), psshData);
                    }
                }
            } else if ("trak".equals(type)) {
                parseMp4Trak(be, pos + header, boxEnd);
            }
            pos = boxEnd;
        }
    }

    private static final class Mp4TrakBuilder {
        long timescale = 1000L;
        long durationTicks = 0L;
        String handler = "";
        String mime;
        int width;
        int height;
        int channels = 2;
        int sampleRate = 44100;
        byte[] csd0;
        int[] sampleSizes = new int[0];
        long[] chunkOffsets = new long[0];
        int[] stscFirstChunk = new int[0];
        int[] stscSamplesPerChunk = new int[0];
        int[] sttsCounts = new int[0];
        int[] sttsDeltas = new int[0];
        java.util.Set<Integer> syncSamples;
    }

    private void parseMp4Trak(ByteBuffer be, int start, int end) {
        Mp4TrakBuilder tb = new Mp4TrakBuilder();
        scanTrakBoxes(be, start, end, tb);
        if (tb.mime == null) {
            if ("soun".equals(tb.handler)) {
                tb.mime = "audio/mp4a-latm";
            } else if ("vide".equals(tb.handler)) {
                tb.mime = "video/avc";
            } else {
                return;
            }
        }
        int trackIndex = mTracks.size();
        MediaFormat fmt = tb.mime.startsWith("video/")
                ? MediaFormat.createVideoFormat(tb.mime, Math.max(1, tb.width), Math.max(1, tb.height))
                : MediaFormat.createAudioFormat(tb.mime, Math.max(8000, tb.sampleRate), Math.max(1, tb.channels));
        if (tb.timescale > 0 && tb.durationTicks > 0) {
            fmt.setLong(MediaFormat.KEY_DURATION, (tb.durationTicks * 1000000L) / tb.timescale);
        }
        if (tb.csd0 != null) {
            fmt.setByteBuffer("csd-0", ByteBuffer.wrap(tb.csd0));
        }
        mTracks.add(fmt);
        mSelected.add(Boolean.FALSE);

        if (tb.sampleSizes.length == 0 || tb.chunkOffsets.length == 0) {
            return;
        }
        int sampleIdx = 0;
        long ptsTicks = 0;
        int sttsEntry = 0;
        int sttsRemaining = tb.sttsCounts.length > 0 ? tb.sttsCounts[0] : Integer.MAX_VALUE;
        int sttsDelta = tb.sttsDeltas.length > 0 ? tb.sttsDeltas[0] : 1024;

        for (int chunk = 0; chunk < tb.chunkOffsets.length && sampleIdx < tb.sampleSizes.length; chunk++) {
            int chunkOneBased = chunk + 1;
            int spc = 1;
            for (int i = 0; i < tb.stscFirstChunk.length; i++) {
                if (chunkOneBased >= tb.stscFirstChunk[i]) {
                    spc = tb.stscSamplesPerChunk[i];
                }
            }
            long offset = tb.chunkOffsets[chunk];
            for (int s = 0; s < spc && sampleIdx < tb.sampleSizes.length; s++) {
                int sz = tb.sampleSizes[sampleIdx];
                if (offset >= 0 && offset + sz <= mData.length) {
                    long ptsUs = tb.timescale > 0 ? (ptsTicks * 1000000L) / tb.timescale : 0L;
                    int flags = (tb.syncSamples == null
                            || tb.syncSamples.contains(Integer.valueOf(sampleIdx + 1)))
                            ? SAMPLE_FLAG_SYNC : 0;
                    mAllSamples.add(new SampleEntry(trackIndex, (int) offset, sz, ptsUs, flags));
                }
                offset += sz;
                sampleIdx++;
                ptsTicks += sttsDelta;
                if (--sttsRemaining <= 0 && sttsEntry + 1 < tb.sttsCounts.length) {
                    sttsEntry++;
                    sttsRemaining = tb.sttsCounts[sttsEntry];
                    sttsDelta = tb.sttsDeltas[sttsEntry];
                }
            }
        }
    }

    private static void scanTrakBoxes(ByteBuffer be, int start, int end, Mp4TrakBuilder tb) {
        int pos = start;
        while (pos + 8 <= end) {
            long size = be.getInt(pos) & 0xFFFFFFFFL;
            byte[] t = new byte[4];
            for (int i = 0; i < 4; i++) {
                t[i] = be.get(pos + 4 + i);
            }
            String type = new String(t, StandardCharsets.US_ASCII);
            int header = 8;
            if (size == 1 && pos + 16 <= end) {
                size = be.getLong(pos + 8);
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header || pos + size > end) {
                break;
            }
            int body = pos + header;
            int boxEnd = (int) (pos + size);
            if ("mdia".equals(type) || "minf".equals(type) || "stbl".equals(type)) {
                scanTrakBoxes(be, body, boxEnd, tb);
            } else if ("mdhd".equals(type) && boxEnd - body >= 20) {
                int v = be.get(body) & 0xFF;
                if (v == 0) {
                    tb.timescale = be.getInt(body + 12) & 0xFFFFFFFFL;
                    tb.durationTicks = be.getInt(body + 16) & 0xFFFFFFFFL;
                } else if (v == 1 && boxEnd - body >= 32) {
                    tb.timescale = be.getInt(body + 20) & 0xFFFFFFFFL;
                    tb.durationTicks = be.getLong(body + 24);
                }
            } else if ("hdlr".equals(type) && boxEnd - body >= 12) {
                byte[] h = {be.get(body + 8), be.get(body + 9), be.get(body + 10), be.get(body + 11)};
                tb.handler = new String(h, StandardCharsets.US_ASCII);
            } else if ("stsd".equals(type) && boxEnd - body >= 16) {
                int entryPos = body + 8;
                if (entryPos + 8 <= boxEnd) {
                    int entrySize = be.getInt(entryPos);
                    byte[] c = {be.get(entryPos + 4), be.get(entryPos + 5), be.get(entryPos + 6), be.get(entryPos + 7)};
                    String fourcc = new String(c, StandardCharsets.US_ASCII);
                    if ("mp4a".equals(fourcc)) {
                        tb.mime = "audio/mp4a-latm";
                        if (entryPos + 36 <= boxEnd) {
                            tb.channels = be.getShort(entryPos + 24) & 0xFFFF;
                            tb.sampleRate = (be.getInt(entryPos + 32) >>> 16) & 0xFFFF;
                        }
                    } else if ("Opus".equals(fourcc)) {
                        tb.mime = "audio/opus";
                    } else if ("avc1".equals(fourcc) || "avc3".equals(fourcc)) {
                        tb.mime = "video/avc";
                        if (entryPos + 36 <= boxEnd) {
                            tb.width = be.getShort(entryPos + 32) & 0xFFFF;
                            tb.height = be.getShort(entryPos + 34) & 0xFFFF;
                        }
                    } else if ("hev1".equals(fourcc) || "hvc1".equals(fourcc)) {
                        tb.mime = "video/hevc";
                        if (entryPos + 36 <= boxEnd) {
                            tb.width = be.getShort(entryPos + 32) & 0xFFFF;
                            tb.height = be.getShort(entryPos + 34) & 0xFFFF;
                        }
                    }
                    if (entrySize > 0 && entryPos + entrySize <= boxEnd) {
                        // keep valid bounds
                    }
                }
            } else if ("stsz".equals(type) && boxEnd - body >= 12) {
                int fixedSize = be.getInt(body + 4);
                int count = be.getInt(body + 8);
                if (count >= 0 && count <= 1000000) {
                    tb.sampleSizes = new int[count];
                    for (int i = 0; i < count; i++) {
                        if (fixedSize != 0) {
                            tb.sampleSizes[i] = fixedSize;
                        } else if (body + 12 + i * 4 + 4 <= boxEnd) {
                            tb.sampleSizes[i] = be.getInt(body + 12 + i * 4);
                        }
                    }
                }
            } else if ("stco".equals(type) && boxEnd - body >= 8) {
                int count = be.getInt(body + 4);
                if (count >= 0 && body + 8L + count * 4L <= boxEnd) {
                    tb.chunkOffsets = new long[count];
                    for (int i = 0; i < count; i++) {
                        tb.chunkOffsets[i] = be.getInt(body + 8 + i * 4) & 0xFFFFFFFFL;
                    }
                }
            } else if ("co64".equals(type) && boxEnd - body >= 8) {
                int count = be.getInt(body + 4);
                if (count >= 0 && body + 8L + count * 8L <= boxEnd) {
                    tb.chunkOffsets = new long[count];
                    for (int i = 0; i < count; i++) {
                        tb.chunkOffsets[i] = be.getLong(body + 8 + i * 8);
                    }
                }
            } else if ("stsc".equals(type) && boxEnd - body >= 8) {
                int count = be.getInt(body + 4);
                if (count >= 0 && body + 8L + count * 12L <= boxEnd) {
                    tb.stscFirstChunk = new int[count];
                    tb.stscSamplesPerChunk = new int[count];
                    for (int i = 0; i < count; i++) {
                        tb.stscFirstChunk[i] = be.getInt(body + 8 + i * 12);
                        tb.stscSamplesPerChunk[i] = be.getInt(body + 12 + i * 12);
                    }
                }
            } else if ("stts".equals(type) && boxEnd - body >= 8) {
                int count = be.getInt(body + 4);
                if (count >= 0 && body + 8L + count * 8L <= boxEnd) {
                    tb.sttsCounts = new int[count];
                    tb.sttsDeltas = new int[count];
                    for (int i = 0; i < count; i++) {
                        tb.sttsCounts[i] = be.getInt(body + 8 + i * 8);
                        tb.sttsDeltas[i] = be.getInt(body + 12 + i * 8);
                    }
                }
            } else if ("stss".equals(type) && boxEnd - body >= 8) {
                int count = be.getInt(body + 4);
                if (count >= 0 && body + 8L + count * 4L <= boxEnd) {
                    tb.syncSamples = new java.util.HashSet<Integer>();
                    for (int i = 0; i < count; i++) {
                        tb.syncSamples.add(Integer.valueOf(be.getInt(body + 8 + i * 4)));
                    }
                }
            }
            pos = boxEnd;
        }
    }

    private void rebuildActiveSamples() {
        mActiveSamples.clear();
        for (int i = 0; i < mAllSamples.size(); i++) {
            SampleEntry se = mAllSamples.get(i);
            if (se.trackIndex >= 0 && se.trackIndex < mSelected.size()
                    && Boolean.TRUE.equals(mSelected.get(se.trackIndex))) {
                mActiveSamples.add(se);
            }
        }
        mSampleCursor = 0;
    }

    public void setMediaCas(MediaCas mediaCas) {
        checkLive();
        mMediaCas = mediaCas;
    }

    public CasInfo getCasInfo(int index) {
        checkLive();
        return null;
    }

    @Override
    protected void finalize() {
        release();
    }

    public void release() {
        mReleased = true;
        mData = null;
        mTracks.clear();
        mSelected.clear();
        mAllSamples.clear();
        mActiveSamples.clear();
        mMediaCas = null;
    }

    public int getTrackCount() {
        checkLive();
        return mTracks.size();
    }

    public DrmInitData getDrmInitData() {
        checkLive();
        if (mPssh.isEmpty()) {
            return null;
        }
        final List<DrmInitData.SchemeInitData> list = new ArrayList<DrmInitData.SchemeInitData>();
        for (Map.Entry<UUID, byte[]> entry : mPssh.entrySet()) {
            list.add(new DrmInitData.SchemeInitData(entry.getKey(), "cenc", entry.getValue().clone()));
        }
        return new DrmInitData() {
            @Override
            public SchemeInitData get(UUID schemeUuid) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).uuid.equals(schemeUuid)) {
                        return list.get(i);
                    }
                }
                return null;
            }

            @Override
            public int getSchemeInitDataCount() {
                return list.size();
            }

            @Override
            public SchemeInitData getSchemeInitDataAt(int index) {
                return list.get(index);
            }
        };
    }

    public List<AudioPresentation> getAudioPresentations(int trackIndex) {
        checkLive();
        if (trackIndex < 0 || trackIndex >= mTracks.size()) {
            throw new IllegalArgumentException("Invalid track index: " + trackIndex);
        }
        return Collections.emptyList();
    }

    public Map<UUID, byte[]> getPsshInfo() {
        checkLive();
        if (mPssh.isEmpty()) {
            return null;
        }
        Map<UUID, byte[]> copy = new LinkedHashMap<UUID, byte[]>();
        for (Map.Entry<UUID, byte[]> e : mPssh.entrySet()) {
            copy.put(e.getKey(), e.getValue().clone());
        }
        return copy;
    }

    public MediaFormat getTrackFormat(int index) {
        checkLive();
        if (index < 0 || index >= mTracks.size()) {
            throw new IllegalArgumentException("Invalid track index: " + index);
        }
        return mTracks.get(index);
    }

    public void selectTrack(int index) {
        checkLive();
        if (index < 0 || index >= mSelected.size()) {
            throw new IllegalArgumentException("Invalid track index: " + index);
        }
        mSelected.set(index, Boolean.TRUE);
        rebuildActiveSamples();
    }

    public void unselectTrack(int index) {
        checkLive();
        if (index < 0 || index >= mSelected.size()) {
            throw new IllegalArgumentException("Invalid track index: " + index);
        }
        mSelected.set(index, Boolean.FALSE);
        rebuildActiveSamples();
    }

    public void seekTo(long timeUs, int mode) {
        checkLive();
        if (mode < SEEK_TO_PREVIOUS_SYNC || mode > SEEK_TO_CLOSEST_SYNC) {
            throw new IllegalArgumentException("Illegal seek mode: " + mode);
        }
        if (mActiveSamples.isEmpty()) {
            mSampleCursor = 0;
            return;
        }
        int bestIdx = 0;
        for (int i = 0; i < mActiveSamples.size(); i++) {
            SampleEntry se = mActiveSamples.get(i);
            if ((se.flags & SAMPLE_FLAG_SYNC) == 0) {
                continue;
            }
            if (mode == SEEK_TO_PREVIOUS_SYNC) {
                if (se.timeUs <= timeUs) {
                    bestIdx = i;
                } else {
                    break;
                }
            } else if (mode == SEEK_TO_NEXT_SYNC) {
                if (se.timeUs >= timeUs) {
                    bestIdx = i;
                    break;
                }
                bestIdx = i;
            } else {
                if (Math.abs(se.timeUs - timeUs) < Math.abs(mActiveSamples.get(bestIdx).timeUs - timeUs)) {
                    bestIdx = i;
                }
            }
        }
        mSampleCursor = bestIdx;
    }

    public boolean advance() {
        checkLive();
        if (mSampleCursor >= mActiveSamples.size()) {
            return false;
        }
        mSampleCursor++;
        return mSampleCursor < mActiveSamples.size();
    }

    public int readSampleData(ByteBuffer byteBuf, int offset) {
        checkLive();
        Objects.requireNonNull(byteBuf, "byteBuf must not be null");
        if (offset < 0 || offset > byteBuf.capacity()) {
            throw new IllegalArgumentException("Invalid buffer offset: " + offset);
        }
        if (mSampleCursor < 0 || mSampleCursor >= mActiveSamples.size() || mData == null) {
            return -1;
        }
        SampleEntry sample = mActiveSamples.get(mSampleCursor);
        if (offset + sample.size > byteBuf.capacity()) {
            throw new IllegalArgumentException("Destination ByteBuffer too small for sample");
        }
        int prevLimit = byteBuf.limit();
        byteBuf.limit(offset + sample.size);
        byteBuf.position(offset);
        byteBuf.put(mData, sample.offset, sample.size);
        byteBuf.limit(Math.max(prevLimit, offset + sample.size));
        byteBuf.position(offset);
        return sample.size;
    }

    public int getSampleTrackIndex() {
        checkLive();
        if (mSampleCursor < 0 || mSampleCursor >= mActiveSamples.size()) {
            return -1;
        }
        return mActiveSamples.get(mSampleCursor).trackIndex;
    }

    public long getSampleTime() {
        checkLive();
        if (mSampleCursor < 0 || mSampleCursor >= mActiveSamples.size()) {
            return -1L;
        }
        return mActiveSamples.get(mSampleCursor).timeUs;
    }

    public long getSampleSize() {
        checkLive();
        if (mSampleCursor < 0 || mSampleCursor >= mActiveSamples.size()) {
            return -1L;
        }
        return mActiveSamples.get(mSampleCursor).size;
    }

    public int getSampleFlags() {
        checkLive();
        if (mSampleCursor < 0 || mSampleCursor >= mActiveSamples.size()) {
            return -1;
        }
        return mActiveSamples.get(mSampleCursor).flags;
    }

    public boolean getSampleCryptoInfo(MediaCodec.CryptoInfo info) {
        checkLive();
        return false;
    }

    public long getCachedDuration() {
        checkLive();
        return -1L;
    }

    public boolean hasCacheReachedEndOfStream() {
        checkLive();
        return true;
    }

    public void setLogSessionId(LogSessionId logSessionId) {
        checkLive();
        mLogSessionId = Objects.requireNonNull(logSessionId, "logSessionId");
    }

    public LogSessionId getLogSessionId() {
        return mLogSessionId;
    }

    public PersistableBundle getMetrics() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(MetricsConstants.FORMAT, mContainerName);
        bundle.putInt(MetricsConstants.TRACKS, mTracks.size());
        if (!mTracks.isEmpty()) {
            String mime = mTracks.get(0).getString(MediaFormat.KEY_MIME);
            if (mime != null) {
                bundle.putString(MetricsConstants.MIME_TYPE, mime);
            }
        }
        return bundle;
    }
}
