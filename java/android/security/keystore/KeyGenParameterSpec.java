// SPDX-License-Identifier: GPL-3.0-only
package android.security.keystore;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Date;
import java.math.BigInteger;
import javax.security.auth.x500.X500Principal;
/** Immutable key-generation requirements; enforcement belongs to the provider. */
public final class KeyGenParameterSpec implements AlgorithmParameterSpec {
    private final Builder b;
    private KeyGenParameterSpec(Builder source){b=new Builder(source.alias,source.purposes);b.size=source.size;b.auth=source.auth;b.strong=source.strong;b.randomized=source.randomized;b.duration=source.duration;b.modes=source.modes.clone();b.paddings=source.paddings.clone();b.digests=source.digests.clone();b.params=source.params;b.signaturePaddings=source.signaturePaddings.clone();b.subject=source.subject;b.serial=source.serial;b.notBefore=copy(source.notBefore);b.notAfter=copy(source.notAfter);b.challenge=source.challenge==null?null:source.challenge.clone();b.start=copy(source.start);b.end=copy(source.end);}
    private static Date copy(Date d){return d==null?null:new Date(d.getTime());}
    public String getKeystoreAlias(){return b.alias;} public int getPurposes(){return b.purposes;} public int getKeySize(){return b.size;}
    public String[] getBlockModes(){return b.modes.clone();} public String[] getEncryptionPaddings(){return b.paddings.clone();} public String[] getDigests(){return b.digests.clone();}
    public boolean isStrongBoxBacked(){return b.strong;} public boolean isUserAuthenticationRequired(){return b.auth;} public int getUserAuthenticationValidityDurationSeconds(){return b.duration;}
    public boolean isRandomizedEncryptionRequired(){return b.randomized;} public AlgorithmParameterSpec getAlgorithmParameterSpec(){return b.params;}
    public byte[] getAttestationChallenge(){return b.challenge==null?null:b.challenge.clone();} public Date getKeyValidityStart(){return copy(b.start);} public Date getKeyValidityForOriginationEnd(){return copy(b.end);}
    public String[] getSignaturePaddings(){return b.signaturePaddings.clone();}
    public X500Principal getCertificateSubject(){return b.subject;} public BigInteger getCertificateSerialNumber(){return b.serial;}
    public Date getCertificateNotBefore(){return copy(b.notBefore);} public Date getCertificateNotAfter(){return copy(b.notAfter);}
    public static final class Builder {
        final String alias;final int purposes;int size=-1,duration=-1;boolean auth,strong,randomized=true;String[] modes={},paddings={},digests={};AlgorithmParameterSpec params;byte[] challenge;Date start,end;
        String[] signaturePaddings={};X500Principal subject=new X500Principal("CN=fake");BigInteger serial=BigInteger.ONE;Date notBefore=new Date(0),notAfter=new Date(2461449600000L);
        public Builder(String alias,int purposes){if(alias==null||alias.isEmpty())throw new IllegalArgumentException("alias");this.alias=alias;this.purposes=purposes;}
        public Builder setKeySize(int v){if(v<0)throw new IllegalArgumentException("size");size=v;return this;}
        public Builder setBlockModes(String... v){modes=v.clone();return this;} public Builder setEncryptionPaddings(String... v){paddings=v.clone();return this;} public Builder setDigests(String... v){digests=v.clone();return this;}
        public Builder setUserAuthenticationRequired(boolean v){auth=v;return this;} public Builder setIsStrongBoxBacked(boolean v){strong=v;return this;}
        public Builder setRandomizedEncryptionRequired(boolean v){randomized=v;return this;} public Builder setUserAuthenticationValidityDurationSeconds(int v){duration=v;return this;}
        public Builder setUserAuthenticationParameters(int timeout,int type){auth=true;duration=timeout;return this;}
        public Builder setInvalidatedByBiometricEnrollment(boolean v){return this;}
        public Builder setAlgorithmParameterSpec(AlgorithmParameterSpec v){params=v;return this;}
        public Builder setAttestationChallenge(byte[] v){challenge=v==null?null:v.clone();return this;}
        public Builder setKeyValidityStart(Date v){start=copy(v);return this;} public Builder setKeyValidityEnd(Date v){end=copy(v);return this;}
        public Builder setSignaturePaddings(String... v){signaturePaddings=v.clone();return this;}
        public Builder setCertificateSubject(X500Principal v){if(v==null)throw new NullPointerException("subject");subject=v;return this;}
        public Builder setCertificateSerialNumber(BigInteger v){if(v==null)throw new NullPointerException("serial");serial=v;return this;}
        public Builder setCertificateNotBefore(Date v){if(v==null)throw new NullPointerException("notBefore");notBefore=copy(v);return this;}
        public Builder setCertificateNotAfter(Date v){if(v==null)throw new NullPointerException("notAfter");notAfter=copy(v);return this;}
        public KeyGenParameterSpec build(){if(notAfter.before(notBefore))throw new IllegalArgumentException("certificate dates");return new KeyGenParameterSpec(this);}
    }
}
