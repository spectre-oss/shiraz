// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

/** Host-attested metadata, not a caller-provided creator identity. */
public final class PendingIntentCapability {
    public final String token, packageName, apkSha256;
    public final int uid, kind, flags;
    PendingIntentCapability(String token,String pkg,String sha,int uid,int kind,int flags) {
        if(token==null||!token.matches("[0-9a-f]{64}")||sha==null||!sha.matches("[0-9a-f]{64}")
                ||pkg==null||pkg.length()>1024||!pkg.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
                ||uid<10000||uid>19999||kind<0||kind>3||(flags&~0x46000000)!=0||(flags&0x06000000)==0x06000000)
            throw new IllegalArgumentException("Invalid host PendingIntent capability");
        this.token=token;packageName=pkg;apkSha256=sha;this.uid=uid;this.kind=kind;this.flags=flags;
    }
}
