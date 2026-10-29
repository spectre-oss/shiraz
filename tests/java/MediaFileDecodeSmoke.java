// SPDX-License-Identifier: GPL-3.0-only
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import shiraz.runtime.NativePlayback;

public final class MediaFileDecodeSmoke {
    public static void main(String[] args)throws Exception {
        Path directory=Paths.get(args[0]);
        for(String name:new String[]{"estimated","indexed"}){
            int expected=Integer.parseInt(new String(Files.readAllBytes(directory.resolve(name+".frames")),StandardCharsets.US_ASCII).trim());
            int[] format=new int[2];
            byte[] pcm=NativePlayback.decodeFile(Files.readAllBytes(directory.resolve(name+".mp3")),format);
            if(format[0]!=44100||format[1]!=1||pcm.length!=expected*2)throw new AssertionError("Decoded shape differs from reference: "+name);
            long energy=0;
            for(int i=0;i<pcm.length;i+=2){int sample=(short)((pcm[i]&255)|(pcm[i+1]<<8));energy+=(long)sample*sample;}
            if(energy/expected<10000)throw new AssertionError("Decoded signal is silent: "+name);
            System.out.println("MEDIA_FILE_EOF_OK "+name+" frames="+expected);
        }
        try{NativePlayback.decodeFile(new byte[0],new int[2]);throw new AssertionError("Empty audio accepted");}catch(IllegalStateException expected){}
        try{NativePlayback.decodeFile(new byte[128],new int[2]);throw new AssertionError("Malformed audio accepted");}catch(IllegalStateException expected){}
    }
}
