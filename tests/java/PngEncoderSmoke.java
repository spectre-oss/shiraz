// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
public final class PngEncoderSmoke {
    static final class Stream extends ByteArrayOutputStream {
        boolean closed;
        public void close() { closed = true; }
    }
    public static void main(String[] args) throws Exception {
        for (Bitmap.Config format : new Bitmap.Config[]{Bitmap.Config.ARGB_8888, Bitmap.Config.ALPHA_8}) {
            Bitmap original = Bitmap.createBitmap(127, 91, format);
            int state = 17;
            for (int y=0; y<91; y++) for (int x=0; x<127; x++) {
                state = state * 1664525 + 1013904223; original.setPixel(x,y,state);
            }
            Stream stream = new Stream();
            if (!original.compress(Bitmap.CompressFormat.PNG, 0, stream) || stream.closed) throw new AssertionError("stream ownership");
            byte[] png = stream.toByteArray();
            Bitmap decoded = BitmapFactory.decodeByteArray(png, 0, png.length);
            if (decoded == null || decoded.getWidth()!=127 || decoded.getHeight()!=91) throw new AssertionError("PNG shape");
            for (int y=0; y<91; y++) for (int x=0; x<127; x++)
                if (original.getPixel(x,y)!=decoded.getPixel(x,y)) throw new AssertionError("PNG pixel roundtrip");
            Stream second = new Stream(); original.compress(Bitmap.CompressFormat.PNG,100,second);
            if (!java.util.Arrays.equals(png,second.toByteArray())) throw new AssertionError("PNG quality should be ignored");
            if (original.compress(Bitmap.CompressFormat.PNG,50,new OutputStream(){public void write(int v)throws IOException{throw new IOException("full");}})) throw new AssertionError("I/O failure hidden");
            try { original.compress(Bitmap.CompressFormat.PNG,101,stream); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
            if (format == Bitmap.Config.ARGB_8888) {
                Stream jpegStream = new Stream();
                if (!original.compress(Bitmap.CompressFormat.JPEG, 80, jpegStream) || jpegStream.closed) throw new AssertionError("JPEG stream ownership");
                byte[] jpeg = jpegStream.toByteArray();
                if (jpeg.length < 100 || (jpeg[0] & 255) != 0xFF || (jpeg[1] & 255) != 0xD8) throw new AssertionError("JPEG header");
                Bitmap jpegDecoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                if (jpegDecoded == null || jpegDecoded.getWidth() != 127 || jpegDecoded.getHeight() != 91) throw new AssertionError("JPEG shape");
                jpegDecoded.recycle();
            }
            original.recycle();
            try { original.compress(Bitmap.CompressFormat.PNG,50,stream); throw new AssertionError(); } catch (IllegalStateException expected) {}
        }
        System.out.println("PNG_STREAMING_NATIVE_DECODER_ROUNDTRIP_OK");
    }
}
