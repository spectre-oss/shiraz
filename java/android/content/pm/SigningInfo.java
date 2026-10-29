// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
/** Signer metadata for the API-27 compatibility profile; not an authenticity verifier. */
public final class SigningInfo implements android.os.Parcelable {
 private final Signature[] signers;
 public SigningInfo(){this(new Signature[0]);}
 public SigningInfo(SigningInfo other){this(other.signers);}
 /** Shiraz metadata constructor. API 27 uses v2 signers and has no rotation history. */
 public SigningInfo(Signature[] current){
  if(current==null)throw new NullPointerException("signers");
  signers=current.clone();for(Signature signer:signers)if(signer==null)throw new NullPointerException("signer");
 }
 public boolean hasMultipleSigners(){return signers.length>1;}
 public boolean hasPastSigningCertificates(){return false;}
 public Signature[] getApkContentsSigners(){return signers.clone();}
 public Signature[] getSigningCertificateHistory(){return hasMultipleSigners()?null:signers.clone();}
 public int describeContents(){return 0;}
 public void writeToParcel(android.os.Parcel parcel,int flags){throw new UnsupportedOperationException("SigningInfo parcel transport is not implemented");}
}
