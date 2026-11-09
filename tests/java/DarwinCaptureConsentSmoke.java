// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.NativeCapture;
public final class DarwinCaptureConsentSmoke {
 public static void main(String[] args){
  int cameras=NativeCapture.cameraCount();if(cameras<0||cameras>1)throw new AssertionError("bounded default camera enumeration");
  boolean mic=false,camera=false,timestamp=false,snapshot=false;
  try{NativeCapture.microphoneOpen(44100,1);}catch(SecurityException expected){mic=true;}
  try{NativeCapture.cameraOpen();}catch(SecurityException expected){camera=true;}
  try{NativeCapture.microphoneTimestamp(-1);}catch(SecurityException expected){timestamp=true;}
  try{NativeCapture.cameraSnapshot(-1);}catch(SecurityException expected){snapshot=true;}
  if(!mic||!camera||!timestamp||!snapshot)throw new AssertionError("capture must require per-app consent before requesting macOS access");
  NativeCapture.close(-1);System.out.println("DARWIN_CAPTURE_CONSENT_OK");
 }
}
