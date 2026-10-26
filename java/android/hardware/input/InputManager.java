// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.input;
/** Fixed aggregate SDL device registry; host peripheral hotplug does not change its identity. */
public final class InputManager {
    private static final InputManager HOST=new InputManager();
    private final java.util.IdentityHashMap<InputDeviceListener,android.os.Handler> listeners=new java.util.IdentityHashMap<InputDeviceListener,android.os.Handler>();
    private InputManager() { }
    public static InputManager getInstance(){return HOST;}
    public int[] getInputDeviceIds(){return android.view.InputDevice.getDeviceIds();}
    public android.view.InputDevice getInputDevice(int id){return android.view.InputDevice.getDevice(id);}
    public synchronized void registerInputDeviceListener(InputDeviceListener listener,android.os.Handler handler){
        if(listener==null)throw new IllegalArgumentException("listener");
        if(!listeners.containsKey(listener))listeners.put(listener,handler==null?new android.os.Handler():handler);
    }
    public synchronized void unregisterInputDeviceListener(InputDeviceListener listener){
        if(listener==null)throw new IllegalArgumentException("listener");listeners.remove(listener);
    }
    public interface InputDeviceListener {
        void onInputDeviceAdded(int deviceId);
        void onInputDeviceRemoved(int deviceId);
        void onInputDeviceChanged(int deviceId);
    }
}
