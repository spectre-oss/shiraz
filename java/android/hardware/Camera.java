// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;
import shiraz.runtime.*;

/** Legacy camera preview subset for Linux V4L2 webcams and QR scanners. */
@Deprecated
public class Camera {
    public static final String ACTION_NEW_PICTURE = "android.hardware.action.NEW_PICTURE";
    public static final String ACTION_NEW_VIDEO = "android.hardware.action.NEW_VIDEO";
    public static final int CAMERA_ERROR_UNKNOWN = 1;
    public static final int CAMERA_ERROR_EVICTED = 2;
    public static final int CAMERA_ERROR_SERVER_DIED = 100;

    @Deprecated
    public static class CameraInfo {
        public static final int CAMERA_FACING_BACK = 0;
        public static final int CAMERA_FACING_FRONT = 1;
        public int facing = 0;
        public int orientation = 0;
        public boolean canDisableShutterSound = true;

        public CameraInfo() {
        }
    }

    @Deprecated
    public class Size {
        public int width;
        public int height;

        public Size(int w, int h) {
            width = w;
            height = h;
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof Camera.Size
                    && ((Size) value).width == width
                    && ((Size) value).height == height;
        }

        @Override
        public int hashCode() {
            return width * 32713 + height;
        }
    }

    @Deprecated
    public interface PreviewCallback {
        void onPreviewFrame(byte[] data, Camera camera);
    }

    @Deprecated
    public interface AutoFocusCallback {
        void onAutoFocus(boolean success, Camera camera);
    }

    @Deprecated
    public interface AutoFocusMoveCallback {
        void onAutoFocusMoving(boolean start, Camera camera);
    }

    @Deprecated
    public interface ErrorCallback {
        void onError(int error, Camera camera);
    }

    @Deprecated
    public interface ShutterCallback {
        void onShutter();
    }

    @Deprecated
    public interface PictureCallback {
        void onPictureTaken(byte[] data, Camera camera);
    }

    @Deprecated
    public interface OnZoomChangeListener {
        void onZoomChange(int zoomValue, boolean stopped, Camera camera);
    }

    @Deprecated
    public static class Face {
        public Rect rect;
        public int score;
        public int id = -1;
        public Point leftEye = null;
        public Point rightEye = null;
        public Point mouth = null;

        public Face() {
        }
    }

    @Deprecated
    public interface FaceDetectionListener {
        void onFaceDetection(Face[] faces, Camera camera);
    }

    @Deprecated
    public static class Area {
        public Rect rect;
        public int weight;

        public Area(Rect rect, int weight) {
            this.rect = rect;
            this.weight = weight;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Area)) {
                return false;
            }
            Area a = (Area) obj;
            if (rect == null) {
                if (a.rect != null) return false;
            } else if (!rect.equals(a.rect)) {
                return false;
            }
            return weight == a.weight;
        }
    }

    private boolean unlocked;
    private boolean takingPicture;
    private int pictureGeneration;
    private final Handler handler;
    private final CapturePermissions permissions;
    private long device;
    private long lease;
    private volatile boolean previewing;
    private volatile boolean released;
    private volatile boolean callbackQueued;
    private volatile int generation;
    private volatile PreviewCallback callback;
    private boolean oneShot;
    private boolean buffered;
    private ErrorCallback error;
    private AutoFocusMoveCallback autoFocusMoveCallback;
    private OnZoomChangeListener zoomChangeListener;
    private FaceDetectionListener faceDetectionListener;
    private boolean shutterSoundEnabled = true;
    private SurfaceHolder holder;
    private SurfaceTexture previewTexture;
    private int orientation;
    private final ArrayDeque<byte[]> buffers = new ArrayDeque<byte[]>();
    private Parameters parameters;

    private Camera() {
        permissions = CapturePermissions.current();
        if (permissions == null) throw new SecurityException("Camera consent is unavailable");
        permissions.require(CapturePermissions.CAMERA);
        handler = new Handler(Looper.getMainLooper());
        long opened = 0;
        try {
            opened = NativeCapture.cameraOpen();
            device = opened;
            int[] size = NativeCapture.cameraSize(device);
            parameters = new Parameters(size[0], size[1]);
            lease = permissions.begin(CapturePermissions.CAMERA, new Runnable() {
                public void run() {
                    release();
                }
            });
        } catch (RuntimeException e) {
            if (opened != 0) NativeCapture.close(opened);
            permissions.failure(CapturePermissions.CAMERA);
            throw e;
        }
    }

    public static int getNumberOfCameras() {
        return NativeCapture.cameraCount();
    }

    public static void getCameraInfo(int id, CameraInfo info) {
        if (id != 0 || getNumberOfCameras() == 0 || info == null) {
            throw new IllegalArgumentException("Camera index");
        }
        info.facing = 0;
        info.orientation = 0;
        info.canDisableShutterSound = true;
    }

    public static Camera open() {
        return open(0);
    }

    public static Camera open(int id) {
        if (id != 0) throw new IllegalArgumentException("Only the default host camera is supported");
        return new Camera();
    }

    @Override
    protected void finalize() {
        release();
    }

    public synchronized Parameters getParameters() {
        live();
        return new Parameters(parameters);
    }

    public synchronized void setParameters(Parameters p) {
        live();
        if (p == null || p.width != parameters.width || p.height != parameters.height) {
            throw new IllegalArgumentException("Unsupported camera dimensions");
        }
        parameters = new Parameters(p);
    }

    public final synchronized void unlock() {
        live();
        unlocked = true;
    }

    public final synchronized void reconnect() throws java.io.IOException {
        if (released) throw new java.io.IOException("Camera released");
        permissions.require(CapturePermissions.CAMERA);
        unlocked = false;
    }

    public final synchronized void lock() {
        if (released) throw new IllegalStateException("Camera released");
        unlocked = false;
    }

    public final synchronized void setPreviewDisplay(SurfaceHolder value) throws java.io.IOException {
        live();
        holder = value;
        previewTexture = null;
    }

    public final synchronized void setPreviewTexture(SurfaceTexture texture) throws java.io.IOException {
        live();
        if (texture != null) {
            if (texture.isReleased()) throw new java.io.IOException("Preview texture released");
            texture.setDefaultBufferSize(parameters.width, parameters.height);
        }
        previewTexture = texture;
        holder = null;
    }

    public final synchronized void setDisplayOrientation(int degrees) {
        if (degrees != 0 && degrees != 90 && degrees != 180 && degrees != 270) {
            throw new IllegalArgumentException("orientation");
        }
        orientation = degrees;
    }

    public final synchronized boolean enableShutterSound(boolean enabled) {
        live();
        shutterSoundEnabled = enabled;
        return true;
    }

    public final synchronized void setErrorCallback(ErrorCallback value) {
        error = value;
    }

    public synchronized void setAutoFocusMoveCallback(AutoFocusMoveCallback cb) {
        autoFocusMoveCallback = cb;
    }

    public final synchronized void setZoomChangeListener(OnZoomChangeListener listener) {
        zoomChangeListener = listener;
    }

    public final synchronized void setFaceDetectionListener(FaceDetectionListener listener) {
        faceDetectionListener = listener;
    }

    public final synchronized void startFaceDetection() {
        live();
        throw new IllegalArgumentException("Face detection is not supported");
    }

    public final synchronized void stopFaceDetection() {
        live();
    }

    public final synchronized void startSmoothZoom(int value) {
        live();
        if (value != 0) {
            throw new IllegalArgumentException("Smooth zoom is not supported");
        }
    }

    public final synchronized void stopSmoothZoom() {
        live();
    }

    public final synchronized void setOneShotPreviewCallback(PreviewCallback value) {
        callback = value;
        oneShot = true;
        buffered = false;
    }

    public final synchronized void setPreviewCallback(PreviewCallback value) {
        callback = value;
        oneShot = false;
        buffered = false;
    }

    public final synchronized void setPreviewCallbackWithBuffer(PreviewCallback value) {
        callback = value;
        oneShot = false;
        buffered = true;
    }

    public final synchronized void addCallbackBuffer(byte[] bytes) {
        live();
        if (bytes == null || bytes.length < parameters.width * parameters.height * 3 / 2) {
            throw new IllegalArgumentException("NV21 preview buffer");
        }
        if (buffers.size() >= 8) throw new IllegalStateException("Preview buffer limit");
        buffers.addLast(bytes);
    }

    public final synchronized void autoFocus(final AutoFocusCallback value) {
        live();
        if (value != null) {
            handler.post(new Runnable() {
                public void run() {
                    if (!released) value.onAutoFocus(true, Camera.this);
                }
            });
        }
    }

    public final void cancelAutoFocus() {
    }

    private void live() {
        if (released) throw new IllegalStateException("Camera released");
        if (unlocked) throw new IllegalStateException("Camera is unlocked for recording");
    }

    public final void takePicture(ShutterCallback shutter, PictureCallback raw, PictureCallback jpeg) {
        takePicture(shutter, raw, null, jpeg);
    }

    public final void takePicture(
            final ShutterCallback shutter,
            final PictureCallback raw,
            final PictureCallback postview,
            final PictureCallback jpeg) {
        final long source;
        final int token;
        final int quality;
        final int rotation;
        synchronized (this) {
            live();
            permissions.require(CapturePermissions.CAMERA);
            if (takingPicture) throw new IllegalStateException("Picture capture already active");
            takingPicture = true;
            previewing = false;
            generation++;
            source = device;
            token = ++pictureGeneration;
            quality = parameters.jpegQuality;
            rotation = parameters.rotation;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    int[] frame = NativeCapture.cameraSnapshot(source);
                    if (frame == null) throw new IllegalStateException("Camera snapshot unavailable");
                    final byte[] bytes = encodePicture(frame, quality, rotation);
                    handler.post(new Runnable() {
                        public void run() {
                            try {
                                if (released
                                        || pictureGeneration != token
                                        || !permissions.granted(CapturePermissions.CAMERA)) {
                                    return;
                                }
                                if (shutter != null) shutter.onShutter();
                                if (raw != null) raw.onPictureTaken(null, Camera.this);
                                if (postview != null) postview.onPictureTaken(null, Camera.this);
                                if (jpeg != null) jpeg.onPictureTaken(bytes, Camera.this);
                            } finally {
                                synchronized (Camera.this) {
                                    if (pictureGeneration == token) takingPicture = false;
                                }
                            }
                        }
                    });
                } catch (RuntimeException failure) {
                    synchronized (Camera.this) {
                        if (pictureGeneration == token) takingPicture = false;
                    }
                    final ErrorCallback target = error;
                    if (target != null) {
                        handler.post(new Runnable() {
                            public void run() {
                                if (!released && pictureGeneration == token) {
                                    target.onError(CAMERA_ERROR_UNKNOWN, Camera.this);
                                }
                            }
                        });
                    } else {
                        android.util.Log.w("Camera", "Host JPEG capture failed", failure);
                    }
                }
            }
        }, "Shiraz camera picture").start();
    }

    private static byte[] encodePicture(int[] frame, int quality, int rotation) {
        if (frame.length < 2 || frame[0] < 1 || frame[1] < 1 || (long) frame[0] * frame[1] != frame.length - 2) {
            throw new IllegalArgumentException("Camera frame");
        }
        Bitmap original = Bitmap.createBitmap(frame[0], frame[1], Bitmap.Config.ARGB_8888);
        Bitmap image = original;
        try {
            original.setPixels(frame, 2, frame[0], 0, 0, frame[0], frame[1]);
            if (rotation != 0) {
                Matrix transform = new Matrix();
                transform.setRotate(rotation);
                image = Bitmap.createBitmap(original, 0, 0, frame[0], frame[1], transform, false);
            }
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            if (!image.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                throw new IllegalStateException("JPEG encoding failed");
            }
            return output.toByteArray();
        } finally {
            if (image != original) image.recycle();
            original.recycle();
        }
    }

    public final synchronized void startPreview() {
        live();
        if (previewing) return;
        previewing = true;
        final int token = ++generation;
        new Thread(new Runnable() {
            public void run() {
                frames(token);
            }
        }, "Shiraz camera preview").start();
    }

    public final synchronized void stopPreview() {
        previewing = false;
        generation++;
    }

    public final void release() {
        long old;
        long id;
        synchronized (this) {
            if (released) return;
            released = true;
            previewing = false;
            generation++;
            pictureGeneration++;
            takingPicture = false;
            old = device;
            device = 0;
            id = lease;
            lease = 0;
            callback = null;
            holder = null;
            previewTexture = null;
            buffers.clear();
        }
        try {
            NativeCapture.close(old);
        } finally {
            permissions.end(id);
        }
    }

    private void frames(final int token) {
        try {
            while (previewing && !released && generation == token) {
                long id;
                SurfaceHolder surface;
                SurfaceTexture texture;
                int rotation;
                synchronized (this) {
                    id = device;
                    surface = holder;
                    texture = previewTexture;
                    rotation = orientation;
                }
                final int[] frame = NativeCapture.cameraFrame(id);
                if (frame == null) continue;
                if (!previewing || released || generation != token) break;
                Surface output = surface != null
                        ? surface.getSurface()
                        : texture != null && !texture.isReleased() ? texture.shirazSurface() : null;
                if (output != null && output.isValid()) {
                    Bitmap bitmap = Bitmap.createBitmap(frame[0], frame[1], Bitmap.Config.ARGB_8888);
                    bitmap.setPixels(frame, 2, frame[0], 0, 0, frame[0], frame[1]);
                    Canvas canvas = null;
                    try {
                        canvas = surface != null ? surface.lockCanvas() : output.lockCanvas(null);
                        if (canvas != null) {
                            canvas.drawColor(0xff000000);
                            if (rotation != 0) {
                                canvas.rotate(rotation, canvas.getWidth() / 2f, canvas.getHeight() / 2f);
                            }
                            canvas.drawBitmap(
                                    bitmap, null, new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), null);
                        }
                    } finally {
                        if (canvas != null) {
                            if (surface != null) surface.unlockCanvasAndPost(canvas);
                            else output.unlockCanvasAndPost(canvas);
                        }
                        bitmap.recycle();
                    }
                }
                final PreviewCallback target;
                final byte[] bytes;
                synchronized (this) {
                    target = callback;
                    if (target == null || callbackQueued) continue;
                    bytes = buffered ? buffers.pollFirst() : new byte[frame[0] * frame[1] * 3 / 2];
                    if (bytes == null) continue;
                    if (oneShot) callback = null;
                    callbackQueued = true;
                }
                nv21(frame, bytes);
                handler.post(new Runnable() {
                    public void run() {
                        try {
                            if (!released && previewing && generation == token) {
                                target.onPreviewFrame(bytes, Camera.this);
                            }
                        } finally {
                            callbackQueued = false;
                        }
                    }
                });
            }
        } catch (RuntimeException failure) {
            if (!released) {
                final ErrorCallback target = error;
                release();
                if (target != null) {
                    handler.post(new Runnable() {
                        public void run() {
                            target.onError(CAMERA_ERROR_UNKNOWN, Camera.this);
                        }
                    });
                }
            }
        }
    }

    private static int clamp(int value) {
        return Math.min(255, Math.max(0, value));
    }

    private static void nv21(int[] frame, byte[] out) {
        int w = frame[0];
        int h = frame[1];
        int uv = w * h;
        if (out.length < uv * 3 / 2) throw new IllegalArgumentException("Preview size changed");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = frame[2 + y * w + x];
                int r = c >> 16 & 255;
                int g = c >> 8 & 255;
                int b = c & 255;
                out[y * w + x] = (byte) clamp(((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
                if ((x & 1) == 0 && (y & 1) == 0 && uv + 1 < out.length) {
                    out[uv++] = (byte) clamp(((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);
                    out[uv++] = (byte) clamp(((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
                }
            }
        }
    }

    @Deprecated
    public class Parameters {
        public static final String WHITE_BALANCE_AUTO = "auto";
        public static final String WHITE_BALANCE_INCANDESCENT = "incandescent";
        public static final String WHITE_BALANCE_FLUORESCENT = "fluorescent";
        public static final String WHITE_BALANCE_WARM_FLUORESCENT = "warm-fluorescent";
        public static final String WHITE_BALANCE_DAYLIGHT = "daylight";
        public static final String WHITE_BALANCE_CLOUDY_DAYLIGHT = "cloudy-daylight";
        public static final String WHITE_BALANCE_TWILIGHT = "twilight";
        public static final String WHITE_BALANCE_SHADE = "shade";

        public static final String EFFECT_NONE = "none";
        public static final String EFFECT_MONO = "mono";
        public static final String EFFECT_NEGATIVE = "negative";
        public static final String EFFECT_SOLARIZE = "solarize";
        public static final String EFFECT_SEPIA = "sepia";
        public static final String EFFECT_POSTERIZE = "posterize";
        public static final String EFFECT_WHITEBOARD = "whiteboard";
        public static final String EFFECT_BLACKBOARD = "blackboard";
        public static final String EFFECT_AQUA = "aqua";

        public static final String ANTIBANDING_AUTO = "auto";
        public static final String ANTIBANDING_50HZ = "50hz";
        public static final String ANTIBANDING_60HZ = "60hz";
        public static final String ANTIBANDING_OFF = "off";

        public static final String FLASH_MODE_OFF = "off";
        public static final String FLASH_MODE_AUTO = "auto";
        public static final String FLASH_MODE_ON = "on";
        public static final String FLASH_MODE_RED_EYE = "red-eye";
        public static final String FLASH_MODE_TORCH = "torch";

        public static final String SCENE_MODE_AUTO = "auto";
        public static final String SCENE_MODE_ACTION = "action";
        public static final String SCENE_MODE_PORTRAIT = "portrait";
        public static final String SCENE_MODE_LANDSCAPE = "landscape";
        public static final String SCENE_MODE_NIGHT = "night";
        public static final String SCENE_MODE_NIGHT_PORTRAIT = "night-portrait";
        public static final String SCENE_MODE_THEATRE = "theatre";
        public static final String SCENE_MODE_BEACH = "beach";
        public static final String SCENE_MODE_SNOW = "snow";
        public static final String SCENE_MODE_SUNSET = "sunset";
        public static final String SCENE_MODE_STEADYPHOTO = "steadyphoto";
        public static final String SCENE_MODE_FIREWORKS = "fireworks";
        public static final String SCENE_MODE_SPORTS = "sports";
        public static final String SCENE_MODE_PARTY = "party";
        public static final String SCENE_MODE_CANDLELIGHT = "candlelight";
        public static final String SCENE_MODE_BARCODE = "barcode";
        public static final String SCENE_MODE_HDR = "hdr";

        public static final String FOCUS_MODE_AUTO = "auto";
        public static final String FOCUS_MODE_INFINITY = "infinity";
        public static final String FOCUS_MODE_MACRO = "macro";
        public static final String FOCUS_MODE_FIXED = "fixed";
        public static final String FOCUS_MODE_EDOF = "edof";
        public static final String FOCUS_MODE_CONTINUOUS_VIDEO = "continuous-video";
        public static final String FOCUS_MODE_CONTINUOUS_PICTURE = "continuous-picture";

        public static final int FOCUS_DISTANCE_NEAR_INDEX = 0;
        public static final int FOCUS_DISTANCE_OPTIMAL_INDEX = 1;
        public static final int FOCUS_DISTANCE_FAR_INDEX = 2;

        public static final int PREVIEW_FPS_MIN_INDEX = 0;
        public static final int PREVIEW_FPS_MAX_INDEX = 1;

        private final int width;
        private final int height;
        private int jpegQuality = 90;
        private int thumbnailQuality = 90;
        private int rotation;
        private boolean recordingHint;
        private int previewFrameRate = 30;
        private final LinkedHashMap<String, String> customMap = new LinkedHashMap<String, String>();

        private Parameters(int width, int height) {
            this.width = width;
            this.height = height;
        }

        private Parameters(Parameters other) {
            width = other.width;
            height = other.height;
            jpegQuality = other.jpegQuality;
            thumbnailQuality = other.thumbnailQuality;
            rotation = other.rotation;
            recordingHint = other.recordingHint;
            previewFrameRate = other.previewFrameRate;
            customMap.putAll(other.customMap);
        }

        public void remove(String key) {
            customMap.remove(key);
        }

        public void set(String key, String value) {
            if (key == null || value == null) return;
            customMap.put(key, value);
        }

        public void set(String key, int value) {
            if (key == null) return;
            customMap.put(key, Integer.toString(value));
        }

        public String get(String key) {
            return customMap.get(key);
        }

        public int getInt(String key) {
            return Integer.parseInt(customMap.get(key));
        }

        public int getMaxNumMeteringAreas() {
            return 0;
        }

        public int getMaxNumFocusAreas() {
            return 0;
        }

        public List<Area> getMeteringAreas() {
            return null;
        }

        public List<Area> getFocusAreas() {
            return null;
        }

        public void setMeteringAreas(List<Area> areas) {
            if (areas != null && !areas.isEmpty()) {
                throw new IllegalArgumentException("Host metering areas unavailable");
            }
        }

        public void setFocusAreas(List<Area> areas) {
            if (areas != null && !areas.isEmpty()) {
                throw new IllegalArgumentException("Host focus areas unavailable");
            }
        }

        public int getMaxNumDetectedFaces() {
            return 0;
        }

        public int getMaxZoom() {
            return 0;
        }

        public int getZoom() {
            return 0;
        }

        public boolean isZoomSupported() {
            return false;
        }

        public boolean isSmoothZoomSupported() {
            return false;
        }

        public List<Integer> getZoomRatios() {
            return Collections.singletonList(Integer.valueOf(100));
        }

        public void setZoom(int zoom) {
            if (zoom != 0) throw new IllegalArgumentException("Host camera zoom unavailable");
        }

        public List<String> getSupportedSceneModes() {
            return null;
        }

        public String getSceneMode() {
            return SCENE_MODE_AUTO;
        }

        public void setSceneMode(String mode) {
            if (mode != null && !SCENE_MODE_AUTO.equals(mode)) {
                throw new IllegalArgumentException("Host camera scene control unavailable");
            }
        }

        public String getWhiteBalance() {
            return WHITE_BALANCE_AUTO;
        }

        public void setWhiteBalance(String value) {
            if (value != null && !WHITE_BALANCE_AUTO.equals(value)) {
                throw new IllegalArgumentException("Host white balance unavailable");
            }
        }

        public List<String> getSupportedWhiteBalance() {
            return Collections.singletonList(WHITE_BALANCE_AUTO);
        }

        public String getColorEffect() {
            return EFFECT_NONE;
        }

        public void setColorEffect(String value) {
            if (value != null && !EFFECT_NONE.equals(value)) {
                throw new IllegalArgumentException("Host color effect unavailable");
            }
        }

        public List<String> getSupportedColorEffects() {
            return Collections.singletonList(EFFECT_NONE);
        }

        public String getAntibanding() {
            return ANTIBANDING_OFF;
        }

        public void setAntibanding(String antibanding) {
            if (antibanding != null && !ANTIBANDING_OFF.equals(antibanding)) {
                throw new IllegalArgumentException("Host antibanding unavailable");
            }
        }

        public List<String> getSupportedAntibanding() {
            return Collections.singletonList(ANTIBANDING_OFF);
        }

        public float getFocalLength() {
            return 4.0f;
        }

        public float getHorizontalViewAngle() {
            return 60.0f;
        }

        public float getVerticalViewAngle() {
            return 45.0f;
        }

        public int getExposureCompensation() {
            return 0;
        }

        public void setExposureCompensation(int value) {
            if (value != 0) throw new IllegalArgumentException("Host exposure compensation unavailable");
        }

        public int getMaxExposureCompensation() {
            return 0;
        }

        public int getMinExposureCompensation() {
            return 0;
        }

        public float getExposureCompensationStep() {
            return 0.0f;
        }

        public void setAutoExposureLock(boolean toggle) {
            if (toggle) throw new IllegalArgumentException("Auto-exposure lock unavailable");
        }

        public boolean getAutoExposureLock() {
            return false;
        }

        public boolean isAutoExposureLockSupported() {
            return false;
        }

        public void setAutoWhiteBalanceLock(boolean toggle) {
            if (toggle) throw new IllegalArgumentException("Auto-white-balance lock unavailable");
        }

        public boolean getAutoWhiteBalanceLock() {
            return false;
        }

        public boolean isAutoWhiteBalanceLockSupported() {
            return false;
        }

        public void getFocusDistances(float[] output) {
            if (output == null || output.length != 3) {
                throw new IllegalArgumentException("output must be a float array with three elements");
            }
            output[FOCUS_DISTANCE_NEAR_INDEX] = Float.POSITIVE_INFINITY;
            output[FOCUS_DISTANCE_OPTIMAL_INDEX] = Float.POSITIVE_INFINITY;
            output[FOCUS_DISTANCE_FAR_INDEX] = Float.POSITIVE_INFINITY;
        }

        public void setGpsLatitude(double latitude) {
            set("gps-latitude", Double.toString(latitude));
        }

        public void setGpsLongitude(double longitude) {
            set("gps-longitude", Double.toString(longitude));
        }

        public void setGpsAltitude(double altitude) {
            set("gps-altitude", Double.toString(altitude));
        }

        public void setGpsTimestamp(long timestamp) {
            set("gps-timestamp", Long.toString(timestamp));
        }

        public void setGpsProcessingMethod(String processing_method) {
            if (processing_method != null) set("gps-processing-method", processing_method);
        }

        public void removeGpsData() {
            remove("gps-latitude");
            remove("gps-longitude");
            remove("gps-altitude");
            remove("gps-timestamp");
            remove("gps-processing-method");
        }

        public void setRecordingHint(boolean hint) {
            recordingHint = hint;
        }

        public boolean isVideoSnapshotSupported() {
            return false;
        }

        public void setPictureFormat(int format) {
            if (format != 256) throw new IllegalArgumentException("Only JPEG pictures are supported");
        }

        public int getPictureFormat() {
            return 256;
        }

        public List<Integer> getSupportedPictureFormats() {
            return Collections.singletonList(Integer.valueOf(256));
        }

        public void setJpegQuality(int quality) {
            if (quality < 1 || quality > 100) throw new IllegalArgumentException("JPEG quality");
            jpegQuality = quality;
        }

        public int getJpegQuality() {
            return jpegQuality;
        }

        public void setJpegThumbnailQuality(int quality) {
            if (quality < 1 || quality > 100) throw new IllegalArgumentException("JPEG thumbnail quality");
            thumbnailQuality = quality;
        }

        public int getJpegThumbnailQuality() {
            return thumbnailQuality;
        }

        public Size getJpegThumbnailSize() {
            return new Size(0, 0);
        }

        public List<Size> getSupportedJpegThumbnailSizes() {
            return Collections.singletonList(getJpegThumbnailSize());
        }

        public void setJpegThumbnailSize(int w, int h) {
            if (w != 0 || h != 0) throw new IllegalArgumentException("JPEG thumbnails unavailable");
        }

        public void setRotation(int value) {
            if (value < 0 || value >= 360 || value % 90 != 0) {
                throw new IllegalArgumentException("Picture rotation");
            }
            rotation = value;
        }

        public Size getPreviewSize() {
            return new Size(width, height);
        }

        public Size getPictureSize() {
            return getPreviewSize();
        }

        public List<Size> getSupportedPreviewSizes() {
            return Collections.singletonList(getPreviewSize());
        }

        public List<Size> getSupportedVideoSizes() {
            return getSupportedPreviewSizes();
        }

        public Size getPreferredPreviewSizeForVideo() {
            return getPreviewSize();
        }

        public List<Size> getSupportedPictureSizes() {
            return getSupportedPreviewSizes();
        }

        public int getPreviewFormat() {
            return 17;
        }

        public void setPreviewFormat(int value) {
            if (value != 17) throw new IllegalArgumentException("Only NV21 preview");
        }

        public List<Integer> getSupportedPreviewFormats() {
            return Collections.singletonList(Integer.valueOf(17));
        }

        @Deprecated
        public void setPreviewFrameRate(int fps) {
            if (fps <= 0) throw new IllegalArgumentException("Preview frame rate");
            previewFrameRate = fps;
        }

        @Deprecated
        public int getPreviewFrameRate() {
            return previewFrameRate;
        }

        @Deprecated
        public List<Integer> getSupportedPreviewFrameRates() {
            return Collections.singletonList(Integer.valueOf(30));
        }

        public void setPreviewSize(int w, int h) {
            if (w != width || h != height) {
                throw new IllegalArgumentException("Unsupported preview size");
            }
        }

        public void setPictureSize(int w, int h) {
            setPreviewSize(w, h);
        }

        public List<String> getSupportedFocusModes() {
            return Collections.singletonList(FOCUS_MODE_FIXED);
        }

        public String getFocusMode() {
            return FOCUS_MODE_FIXED;
        }

        public void setFocusMode(String value) {
            if (!FOCUS_MODE_FIXED.equals(value)) {
                throw new IllegalArgumentException("Only fixed webcam focus");
            }
        }

        public List<String> getSupportedFlashModes() {
            return Collections.singletonList(FLASH_MODE_OFF);
        }

        public String getFlashMode() {
            return FLASH_MODE_OFF;
        }

        public void setFlashMode(String value) {
            if (!FLASH_MODE_OFF.equals(value)) {
                throw new IllegalArgumentException("Webcam flash unavailable");
            }
        }

        public boolean isVideoStabilizationSupported() {
            return false;
        }

        public boolean getVideoStabilization() {
            return false;
        }

        public void setVideoStabilization(boolean value) {
            if (value) throw new UnsupportedOperationException("Video stabilization unavailable");
        }

        public List<int[]> getSupportedPreviewFpsRange() {
            return Collections.singletonList(new int[] { 15000, 30000 });
        }

        public void getPreviewFpsRange(int[] out) {
            if (out == null || out.length < 2) throw new IllegalArgumentException("FPS range");
            out[0] = 15000;
            out[1] = 30000;
        }

        public void setPreviewFpsRange(int min, int max) {
            if (min != 15000 || max != 30000) throw new IllegalArgumentException("FPS range");
        }

        public String flatten() {
            return "preview-size=" + width + "x" + height
                    + ";picture-size=" + width + "x" + height
                    + ";preview-format=yuv420sp;focus-mode=fixed;flash-mode=off";
        }

        public void unflatten(String value) {
            if (value == null || !value.equals(flatten())) {
                throw new IllegalArgumentException("Unsupported camera parameter snapshot");
            }
        }
    }
}
