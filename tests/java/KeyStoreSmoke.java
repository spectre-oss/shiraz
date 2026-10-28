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
  if(args.length>1){if(store.getKey("test",null)==null)throw new AssertionError("lost key");store.deleteEntry("test");System.out.println("KEYSTORE_RESTART_OK");return;}
  KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder("test",3).setKeySize(256).build());SecretKey key=generator.generateKey();
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);byte[] encrypted=cipher.doFinal(new byte[]{1,2,3});
  Cipher decrypt=Cipher.getInstance("AES/GCM/NoPadding");decrypt.init(Cipher.DECRYPT_MODE,store.getKey("test",null),new javax.crypto.spec.GCMParameterSpec(128,cipher.getIV()));if(decrypt.doFinal(encrypted)[2]!=3)throw new AssertionError("roundtrip");
  try{generator.init(new KeyGenParameterSpec.Builder("hardware",3).setIsStrongBoxBacked(true).build());throw new AssertionError("hardware accepted");}catch(InvalidAlgorithmParameterException expected){}
  System.out.println("KEYSTORE_CRYPTO_OK");
 }
}
