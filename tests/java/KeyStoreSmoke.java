// SPDX-License-Identifier: GPL-3.0-only
import java.io.File;
import java.security.*;
import javax.crypto.*;
import android.security.keystore.KeyGenParameterSpec;
import shiraz.runtime.HostKeyStoreProvider;
public class KeyStoreSmoke {
 public static void main(String[] args)throws Exception {
  File dir=new File(args[0]);dir.mkdirs();HostKeyStoreProvider.install(dir);
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(args.length>1){if(store.getKey("test",null)==null)throw new AssertionError("lost key");checkRsa(store);store.deleteEntry("rsa");if(store.containsAlias("rsa")||store.getCertificate("rsa")!=null)throw new AssertionError("RSA deletion");store.deleteEntry("test");System.out.println("KEYSTORE_RESTART_OK");return;}
  KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder("test",3).setKeySize(256).build());SecretKey key=generator.generateKey();
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);byte[] encrypted=cipher.doFinal(new byte[]{1,2,3});
  Cipher decrypt=Cipher.getInstance("AES/GCM/NoPadding");decrypt.init(Cipher.DECRYPT_MODE,store.getKey("test",null),new javax.crypto.spec.GCMParameterSpec(128,cipher.getIV()));if(decrypt.doFinal(encrypted)[2]!=3)throw new AssertionError("roundtrip");
  try{generator.init(new KeyGenParameterSpec.Builder("hardware",3).setIsStrongBoxBacked(true).build());throw new AssertionError("hardware accepted");}catch(InvalidAlgorithmParameterException expected){}
  KeyPairGenerator rsa=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
  rsa.initialize(new KeyGenParameterSpec.Builder("rsa",12).setKeySize(2048).setDigests("SHA-256").setSignaturePaddings("PKCS1").build());
  rsa.generateKeyPair();checkRsa(store);
  if(store.size()!=2)throw new AssertionError("metadata counted as aliases");
  try{rsa.initialize(new KeyGenParameterSpec.Builder("attested",12).setAttestationChallenge(new byte[]{1}).build());throw new AssertionError("attestation accepted");}catch(InvalidAlgorithmParameterException expected){}
  try{rsa.generateKeyPair();throw new AssertionError("failed initialize reused key spec");}catch(IllegalStateException expected){}
  System.out.println("KEYSTORE_CRYPTO_OK");
 }
 static void checkRsa(KeyStore store)throws Exception {
  PrivateKey key=(PrivateKey)store.getKey("rsa",null);
  java.security.cert.X509Certificate certificate=(java.security.cert.X509Certificate)store.getCertificate("rsa");
  certificate.verify(certificate.getPublicKey());
  if(!"rsa".equals(store.getCertificateAlias(certificate))||store.getCertificateChain("rsa").length!=1)throw new AssertionError("certificate lookup");
  Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(key);signature.update(new byte[]{4,5,6});byte[] signed=signature.sign();
  signature.initVerify(certificate);signature.update(new byte[]{4,5,6});if(!signature.verify(signed))throw new AssertionError("RSA signature");
  if(!(store.getEntry("rsa",null) instanceof KeyStore.PrivateKeyEntry))throw new AssertionError("private key entry");
 }
}
