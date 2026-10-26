// SPDX-License-Identifier: GPL-3.0-only
package android.media;
public final class MediaCodecList {
 public static final int REGULAR_CODECS=0,ALL_CODECS=1;
 public MediaCodecList(int kind){if(kind!=0&&kind!=1)throw new IllegalArgumentException("codec list kind");}
 private static final String[] VIDEO=shiraz.runtime.NativeVideo.codecs();
 public static int getCodecCount(){return 2+VIDEO.length;}
 public static MediaCodecInfo getCodecInfoAt(int index){if(index<0||index>=getCodecCount())throw new IllegalArgumentException("codec index");return index<2?new MediaCodecInfo(index==1):new MediaCodecInfo(VIDEO[index-2]);}
 public MediaCodecInfo[] getCodecInfos(){MediaCodecInfo[] all=new MediaCodecInfo[getCodecCount()];for(int i=0;i<all.length;i++)all[i]=getCodecInfoAt(i);return all;}
}
