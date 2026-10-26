// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
/** Declared feature/version metadata. */
public class FeatureInfo {
 public static final int FLAG_REQUIRED=1,GL_ES_VERSION_UNDEFINED=0;
 public String name;public int version,reqGlEsVersion,flags;
 public FeatureInfo(){}
 public FeatureInfo(FeatureInfo other){name=other.name;version=other.version;reqGlEsVersion=other.reqGlEsVersion;flags=other.flags;}
 public String getGlEsVersion(){return (reqGlEsVersion>>>16)+"."+(reqGlEsVersion&65535);}
}
