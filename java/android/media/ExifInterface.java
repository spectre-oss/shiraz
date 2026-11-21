// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import shiraz.runtime.NativeFd;

public class ExifInterface {
    public static final int ORIENTATION_UNDEFINED = 0;
    public static final int ORIENTATION_NORMAL = 1;
    public static final int ORIENTATION_FLIP_HORIZONTAL = 2;
    public static final int ORIENTATION_ROTATE_180 = 3;
    public static final int ORIENTATION_FLIP_VERTICAL = 4;
    public static final int ORIENTATION_TRANSPOSE = 5;
    public static final int ORIENTATION_ROTATE_90 = 6;
    public static final int ORIENTATION_TRANSVERSE = 7;
    public static final int ORIENTATION_ROTATE_270 = 8;

    public static final int STREAM_TYPE_FULL_IMAGE_DATA = 0;
    public static final int STREAM_TYPE_EXIF_DATA_ONLY = 1;

    public static final int WHITEBALANCE_AUTO = 0;
    public static final int WHITEBALANCE_MANUAL = 1;

    public static final String TAG_APERTURE = "FNumber";
    public static final String TAG_APERTURE_VALUE = "ApertureValue";
    public static final String TAG_ARTIST = "Artist";
    public static final String TAG_BITS_PER_SAMPLE = "BitsPerSample";
    public static final String TAG_BRIGHTNESS_VALUE = "BrightnessValue";
    public static final String TAG_CFA_PATTERN = "CFAPattern";
    public static final String TAG_COLOR_SPACE = "ColorSpace";
    public static final String TAG_COMPONENTS_CONFIGURATION = "ComponentsConfiguration";
    public static final String TAG_COMPRESSED_BITS_PER_PIXEL = "CompressedBitsPerPixel";
    public static final String TAG_COMPRESSION = "Compression";
    public static final String TAG_CONTRAST = "Contrast";
    public static final String TAG_COPYRIGHT = "Copyright";
    public static final String TAG_CUSTOM_RENDERED = "CustomRendered";
    public static final String TAG_DATETIME = "DateTime";
    public static final String TAG_DATETIME_DIGITIZED = "DateTimeDigitized";
    public static final String TAG_DATETIME_ORIGINAL = "DateTimeOriginal";
    public static final String TAG_DEFAULT_CROP_SIZE = "DefaultCropSize";
    public static final String TAG_DEVICE_SETTING_DESCRIPTION = "DeviceSettingDescription";
    public static final String TAG_DIGITAL_ZOOM_RATIO = "DigitalZoomRatio";
    public static final String TAG_DNG_VERSION = "DNGVersion";
    public static final String TAG_EXIF_VERSION = "ExifVersion";
    public static final String TAG_EXPOSURE_BIAS_VALUE = "ExposureBiasValue";
    public static final String TAG_EXPOSURE_INDEX = "ExposureIndex";
    public static final String TAG_EXPOSURE_MODE = "ExposureMode";
    public static final String TAG_EXPOSURE_PROGRAM = "ExposureProgram";
    public static final String TAG_EXPOSURE_TIME = "ExposureTime";
    public static final String TAG_FILE_SOURCE = "FileSource";
    public static final String TAG_FLASH = "Flash";
    public static final String TAG_FLASHPIX_VERSION = "FlashpixVersion";
    public static final String TAG_FLASH_ENERGY = "FlashEnergy";
    public static final String TAG_FOCAL_LENGTH = "FocalLength";
    public static final String TAG_FOCAL_LENGTH_IN_35MM_FILM = "FocalLengthIn35mmFilm";
    public static final String TAG_FOCAL_PLANE_RESOLUTION_UNIT = "FocalPlaneResolutionUnit";
    public static final String TAG_FOCAL_PLANE_X_RESOLUTION = "FocalPlaneXResolution";
    public static final String TAG_FOCAL_PLANE_Y_RESOLUTION = "FocalPlaneYResolution";
    public static final String TAG_F_NUMBER = "FNumber";
    public static final String TAG_GAIN_CONTROL = "GainControl";
    public static final String TAG_GPS_ALTITUDE = "GPSAltitude";
    public static final String TAG_GPS_ALTITUDE_REF = "GPSAltitudeRef";
    public static final String TAG_GPS_AREA_INFORMATION = "GPSAreaInformation";
    public static final String TAG_GPS_DATESTAMP = "GPSDateStamp";
    public static final String TAG_GPS_DEST_BEARING = "GPSDestBearing";
    public static final String TAG_GPS_DEST_BEARING_REF = "GPSDestBearingRef";
    public static final String TAG_GPS_DEST_DISTANCE = "GPSDestDistance";
    public static final String TAG_GPS_DEST_DISTANCE_REF = "GPSDestDistanceRef";
    public static final String TAG_GPS_DEST_LATITUDE = "GPSDestLatitude";
    public static final String TAG_GPS_DEST_LATITUDE_REF = "GPSDestLatitudeRef";
    public static final String TAG_GPS_DEST_LONGITUDE = "GPSDestLongitude";
    public static final String TAG_GPS_DEST_LONGITUDE_REF = "GPSDestLongitudeRef";
    public static final String TAG_GPS_DIFFERENTIAL = "GPSDifferential";
    public static final String TAG_GPS_DOP = "GPSDOP";
    public static final String TAG_GPS_IMG_DIRECTION = "GPSImgDirection";
    public static final String TAG_GPS_IMG_DIRECTION_REF = "GPSImgDirectionRef";
    public static final String TAG_GPS_LATITUDE = "GPSLatitude";
    public static final String TAG_GPS_LATITUDE_REF = "GPSLatitudeRef";
    public static final String TAG_GPS_LONGITUDE = "GPSLongitude";
    public static final String TAG_GPS_LONGITUDE_REF = "GPSLongitudeRef";
    public static final String TAG_GPS_MAP_DATUM = "GPSMapDatum";
    public static final String TAG_GPS_MEASURE_MODE = "GPSMeasureMode";
    public static final String TAG_GPS_PROCESSING_METHOD = "GPSProcessingMethod";
    public static final String TAG_GPS_SATELLITES = "GPSSatellites";
    public static final String TAG_GPS_SPEED = "GPSSpeed";
    public static final String TAG_GPS_SPEED_REF = "GPSSpeedRef";
    public static final String TAG_GPS_STATUS = "GPSStatus";
    public static final String TAG_GPS_TIMESTAMP = "GPSTimeStamp";
    public static final String TAG_GPS_TRACK = "GPSTrack";
    public static final String TAG_GPS_TRACK_REF = "GPSTrackRef";
    public static final String TAG_GPS_VERSION_ID = "GPSVersionID";
    public static final String TAG_IMAGE_DESCRIPTION = "ImageDescription";
    public static final String TAG_IMAGE_LENGTH = "ImageLength";
    public static final String TAG_IMAGE_UNIQUE_ID = "ImageUniqueID";
    public static final String TAG_IMAGE_WIDTH = "ImageWidth";
    public static final String TAG_INTEROPERABILITY_INDEX = "InteroperabilityIndex";
    public static final String TAG_ISO = "ISOSpeedRatings";
    public static final String TAG_ISO_SPEED_RATINGS = "ISOSpeedRatings";
    public static final String TAG_JPEG_INTERCHANGE_FORMAT = "JPEGInterchangeFormat";
    public static final String TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = "JPEGInterchangeFormatLength";
    public static final String TAG_LIGHT_SOURCE = "LightSource";
    public static final String TAG_MAKE = "Make";
    public static final String TAG_MAKER_NOTE = "MakerNote";
    public static final String TAG_MAX_APERTURE_VALUE = "MaxApertureValue";
    public static final String TAG_METERING_MODE = "MeteringMode";
    public static final String TAG_MODEL = "Model";
    public static final String TAG_NEW_SUBFILE_TYPE = "NewSubfileType";
    public static final String TAG_OECF = "OECF";
    public static final String TAG_OFFSET_TIME = "OffsetTime";
    public static final String TAG_OFFSET_TIME_DIGITIZED = "OffsetTimeDigitized";
    public static final String TAG_OFFSET_TIME_ORIGINAL = "OffsetTimeOriginal";
    public static final String TAG_ORF_ASPECT_FRAME = "AspectFrame";
    public static final String TAG_ORF_PREVIEW_IMAGE_LENGTH = "PreviewImageLength";
    public static final String TAG_ORF_PREVIEW_IMAGE_START = "PreviewImageStart";
    public static final String TAG_ORF_THUMBNAIL_IMAGE = "ThumbnailImage";
    public static final String TAG_ORIENTATION = "Orientation";
    public static final String TAG_PHOTOMETRIC_INTERPRETATION = "PhotometricInterpretation";
    public static final String TAG_PIXEL_X_DIMENSION = "PixelXDimension";
    public static final String TAG_PIXEL_Y_DIMENSION = "PixelYDimension";
    public static final String TAG_PLANAR_CONFIGURATION = "PlanarConfiguration";
    public static final String TAG_PRIMARY_CHROMATICITIES = "PrimaryChromaticities";
    public static final String TAG_REFERENCE_BLACK_WHITE = "ReferenceBlackWhite";
    public static final String TAG_RELATED_SOUND_FILE = "RelatedSoundFile";
    public static final String TAG_RESOLUTION_UNIT = "ResolutionUnit";
    public static final String TAG_ROWS_PER_STRIP = "RowsPerStrip";
    public static final String TAG_RW2_ISO = "ISO";
    public static final String TAG_RW2_JPG_FROM_RAW = "JpgFromRaw";
    public static final String TAG_RW2_SENSOR_BOTTOM_BORDER = "SensorBottomBorder";
    public static final String TAG_RW2_SENSOR_LEFT_BORDER = "SensorLeftBorder";
    public static final String TAG_RW2_SENSOR_RIGHT_BORDER = "SensorRightBorder";
    public static final String TAG_RW2_SENSOR_TOP_BORDER = "SensorTopBorder";
    public static final String TAG_SAMPLES_PER_PIXEL = "SamplesPerPixel";
    public static final String TAG_SATURATION = "Saturation";
    public static final String TAG_SCENE_CAPTURE_TYPE = "SceneCaptureType";
    public static final String TAG_SCENE_TYPE = "SceneType";
    public static final String TAG_SENSING_METHOD = "SensingMethod";
    public static final String TAG_SHARPNESS = "Sharpness";
    public static final String TAG_SHUTTER_SPEED_VALUE = "ShutterSpeedValue";
    public static final String TAG_SOFTWARE = "Software";
    public static final String TAG_SPATIAL_FREQUENCY_RESPONSE = "SpatialFrequencyResponse";
    public static final String TAG_SPECTRAL_SENSITIVITY = "SpectralSensitivity";
    public static final String TAG_STRIP_BYTE_COUNTS = "StripByteCounts";
    public static final String TAG_STRIP_OFFSETS = "StripOffsets";
    public static final String TAG_SUBFILE_TYPE = "SubfileType";
    public static final String TAG_SUBJECT_AREA = "SubjectArea";
    public static final String TAG_SUBJECT_DISTANCE = "SubjectDistance";
    public static final String TAG_SUBJECT_DISTANCE_RANGE = "SubjectDistanceRange";
    public static final String TAG_SUBJECT_LOCATION = "SubjectLocation";
    public static final String TAG_SUBSEC_TIME = "SubSecTime";
    public static final String TAG_SUBSEC_TIME_DIG = "SubSecTimeDigitized";
    public static final String TAG_SUBSEC_TIME_DIGITIZED = "SubSecTimeDigitized";
    public static final String TAG_SUBSEC_TIME_ORIG = "SubSecTimeOriginal";
    public static final String TAG_SUBSEC_TIME_ORIGINAL = "SubSecTimeOriginal";
    public static final String TAG_THUMBNAIL_IMAGE_LENGTH = "ThumbnailImageLength";
    public static final String TAG_THUMBNAIL_IMAGE_WIDTH = "ThumbnailImageWidth";
    public static final String TAG_THUMBNAIL_ORIENTATION = "ThumbnailOrientation";
    public static final String TAG_TRANSFER_FUNCTION = "TransferFunction";
    public static final String TAG_USER_COMMENT = "UserComment";
    public static final String TAG_WHITE_BALANCE = "WhiteBalance";
    public static final String TAG_WHITE_POINT = "WhitePoint";
    public static final String TAG_XMP = "Xmp";
    public static final String TAG_X_RESOLUTION = "XResolution";
    public static final String TAG_Y_CB_CR_COEFFICIENTS = "YCbCrCoefficients";
    public static final String TAG_Y_CB_CR_POSITIONING = "YCbCrPositioning";
    public static final String TAG_Y_CB_CR_SUB_SAMPLING = "YCbCrSubSampling";
    public static final String TAG_Y_RESOLUTION = "YResolution";

    private static final int IFD_TYPE_PRIMARY = 0;
    private static final int IFD_TYPE_EXIF = 1;
    private static final int IFD_TYPE_GPS = 2;
    private static final int IFD_TYPE_INTEROP = 3;
    private static final int IFD_TYPE_THUMBNAIL = 4;

    private static final int TIFF_BYTE = 1;
    private static final int TIFF_ASCII = 2;
    private static final int TIFF_USHORT = 3;
    private static final int TIFF_ULONG = 4;
    private static final int TIFF_URATIONAL = 5;
    private static final int TIFF_SBYTE = 6;
    private static final int TIFF_UNDEFINED = 7;
    private static final int TIFF_SSHORT = 8;
    private static final int TIFF_SLONG = 9;
    private static final int TIFF_SRATIONAL = 10;
    private static final int TIFF_SINGLE = 11;
    private static final int TIFF_DOUBLE = 12;

    private static final int[] TYPE_SIZES = {0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8};

    private static final Map<Integer, String> PRIMARY_EXIF_TAGS = new HashMap<Integer, String>();
    private static final Map<Integer, String> GPS_TAGS = new HashMap<Integer, String>();
    private static final Map<String, TagSpec> TAG_SPECS = new HashMap<String, TagSpec>();
    private static final Set<String> SUPPORTED_MIMES = new HashSet<String>();

    private static final class TagSpec {
        final int number;
        final int format;
        final int ifd;

        TagSpec(int number, int format, int ifd) {
            this.number = number;
            this.format = format;
            this.ifd = ifd;
        }
    }

    private static void reg(int number, String name, int format, int ifd) {
        if (ifd == IFD_TYPE_GPS) {
            GPS_TAGS.put(Integer.valueOf(number), name);
        } else {
            PRIMARY_EXIF_TAGS.put(Integer.valueOf(number), name);
        }
        if (!TAG_SPECS.containsKey(name)) {
            TAG_SPECS.put(name, new TagSpec(number, format, ifd));
        }
    }

    static {
        SUPPORTED_MIMES.add("image/jpeg");
        SUPPORTED_MIMES.add("image/jpg");
        SUPPORTED_MIMES.add("image/png");
        SUPPORTED_MIMES.add("image/webp");
        SUPPORTED_MIMES.add("image/heif");
        SUPPORTED_MIMES.add("image/heic");
        SUPPORTED_MIMES.add("image/avif");
        SUPPORTED_MIMES.add("image/x-adobe-dng");
        SUPPORTED_MIMES.add("image/tiff");

        // Primary / IFD0 tags
        reg(0x00FE, TAG_NEW_SUBFILE_TYPE, TIFF_ULONG, IFD_TYPE_PRIMARY);
        reg(0x00FF, TAG_SUBFILE_TYPE, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x0100, TAG_IMAGE_WIDTH, TIFF_ULONG, IFD_TYPE_PRIMARY);
        reg(0x0101, TAG_IMAGE_LENGTH, TIFF_ULONG, IFD_TYPE_PRIMARY);
        reg(0x0102, TAG_BITS_PER_SAMPLE, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x0103, TAG_COMPRESSION, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x0106, TAG_PHOTOMETRIC_INTERPRETATION, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x010E, TAG_IMAGE_DESCRIPTION, TIFF_ASCII, IFD_TYPE_PRIMARY);
        reg(0x010F, TAG_MAKE, TIFF_ASCII, IFD_TYPE_PRIMARY);
        reg(0x0110, TAG_MODEL, TIFF_ASCII, IFD_TYPE_PRIMARY);
        reg(0x0111, TAG_STRIP_OFFSETS, TIFF_ULONG, IFD_TYPE_PRIMARY);
        reg(0x0112, TAG_ORIENTATION, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x0115, TAG_SAMPLES_PER_PIXEL, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x0116, TAG_ROWS_PER_STRIP, TIFF_ULONG, IFD_TYPE_PRIMARY);
        reg(0x0117, TAG_STRIP_BYTE_COUNTS, TIFF_ULONG, IFD_TYPE_PRIMARY);
        reg(0x011A, TAG_X_RESOLUTION, TIFF_URATIONAL, IFD_TYPE_PRIMARY);
        reg(0x011B, TAG_Y_RESOLUTION, TIFF_URATIONAL, IFD_TYPE_PRIMARY);
        reg(0x011C, TAG_PLANAR_CONFIGURATION, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x0128, TAG_RESOLUTION_UNIT, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x012D, TAG_TRANSFER_FUNCTION, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x0131, TAG_SOFTWARE, TIFF_ASCII, IFD_TYPE_PRIMARY);
        reg(0x0132, TAG_DATETIME, TIFF_ASCII, IFD_TYPE_PRIMARY);
        reg(0x013B, TAG_ARTIST, TIFF_ASCII, IFD_TYPE_PRIMARY);
        reg(0x013E, TAG_WHITE_POINT, TIFF_URATIONAL, IFD_TYPE_PRIMARY);
        reg(0x013F, TAG_PRIMARY_CHROMATICITIES, TIFF_URATIONAL, IFD_TYPE_PRIMARY);
        reg(0x0201, TAG_JPEG_INTERCHANGE_FORMAT, TIFF_ULONG, IFD_TYPE_THUMBNAIL);
        reg(0x0202, TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, TIFF_ULONG, IFD_TYPE_THUMBNAIL);
        reg(0x0211, TAG_Y_CB_CR_COEFFICIENTS, TIFF_URATIONAL, IFD_TYPE_PRIMARY);
        reg(0x0212, TAG_Y_CB_CR_SUB_SAMPLING, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x0213, TAG_Y_CB_CR_POSITIONING, TIFF_USHORT, IFD_TYPE_PRIMARY);
        reg(0x0214, TAG_REFERENCE_BLACK_WHITE, TIFF_URATIONAL, IFD_TYPE_PRIMARY);
        reg(0x02BC, TAG_XMP, TIFF_BYTE, IFD_TYPE_PRIMARY);
        reg(0x8298, TAG_COPYRIGHT, TIFF_ASCII, IFD_TYPE_PRIMARY);
        reg(0xC612, TAG_DNG_VERSION, TIFF_BYTE, IFD_TYPE_PRIMARY);
        reg(0xC620, TAG_DEFAULT_CROP_SIZE, TIFF_ULONG, IFD_TYPE_PRIMARY);

        // Exif SubIFD tags
        reg(0x829A, TAG_EXPOSURE_TIME, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0x829D, TAG_F_NUMBER, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0x8822, TAG_EXPOSURE_PROGRAM, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0x8824, TAG_SPECTRAL_SENSITIVITY, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0x8827, TAG_ISO_SPEED_RATINGS, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0x8828, TAG_OECF, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0x9000, TAG_EXIF_VERSION, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0x9003, TAG_DATETIME_ORIGINAL, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0x9004, TAG_DATETIME_DIGITIZED, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0x9010, TAG_OFFSET_TIME, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0x9011, TAG_OFFSET_TIME_ORIGINAL, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0x9012, TAG_OFFSET_TIME_DIGITIZED, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0x9101, TAG_COMPONENTS_CONFIGURATION, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0x9102, TAG_COMPRESSED_BITS_PER_PIXEL, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0x9201, TAG_SHUTTER_SPEED_VALUE, TIFF_SRATIONAL, IFD_TYPE_EXIF);
        reg(0x9202, TAG_APERTURE_VALUE, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0x9203, TAG_BRIGHTNESS_VALUE, TIFF_SRATIONAL, IFD_TYPE_EXIF);
        reg(0x9204, TAG_EXPOSURE_BIAS_VALUE, TIFF_SRATIONAL, IFD_TYPE_EXIF);
        reg(0x9205, TAG_MAX_APERTURE_VALUE, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0x9206, TAG_SUBJECT_DISTANCE, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0x9207, TAG_METERING_MODE, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0x9208, TAG_LIGHT_SOURCE, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0x9209, TAG_FLASH, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0x920A, TAG_FOCAL_LENGTH, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0x9214, TAG_SUBJECT_AREA, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0x927C, TAG_MAKER_NOTE, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0x9286, TAG_USER_COMMENT, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0x9290, TAG_SUBSEC_TIME, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0x9291, TAG_SUBSEC_TIME_ORIGINAL, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0x9292, TAG_SUBSEC_TIME_DIGITIZED, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0xA000, TAG_FLASHPIX_VERSION, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0xA001, TAG_COLOR_SPACE, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA002, TAG_PIXEL_X_DIMENSION, TIFF_ULONG, IFD_TYPE_EXIF);
        reg(0xA003, TAG_PIXEL_Y_DIMENSION, TIFF_ULONG, IFD_TYPE_EXIF);
        reg(0xA004, TAG_RELATED_SOUND_FILE, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0xA20B, TAG_FLASH_ENERGY, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0xA20C, TAG_SPATIAL_FREQUENCY_RESPONSE, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0xA20E, TAG_FOCAL_PLANE_X_RESOLUTION, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0xA20F, TAG_FOCAL_PLANE_Y_RESOLUTION, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0xA210, TAG_FOCAL_PLANE_RESOLUTION_UNIT, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA214, TAG_SUBJECT_LOCATION, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA215, TAG_EXPOSURE_INDEX, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0xA217, TAG_SENSING_METHOD, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA300, TAG_FILE_SOURCE, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0xA301, TAG_SCENE_TYPE, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0xA302, TAG_CFA_PATTERN, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0xA401, TAG_CUSTOM_RENDERED, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA402, TAG_EXPOSURE_MODE, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA403, TAG_WHITE_BALANCE, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA404, TAG_DIGITAL_ZOOM_RATIO, TIFF_URATIONAL, IFD_TYPE_EXIF);
        reg(0xA405, TAG_FOCAL_LENGTH_IN_35MM_FILM, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA406, TAG_SCENE_CAPTURE_TYPE, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA407, TAG_GAIN_CONTROL, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA408, TAG_CONTRAST, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA409, TAG_SATURATION, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA40A, TAG_SHARPNESS, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA40B, TAG_DEVICE_SETTING_DESCRIPTION, TIFF_UNDEFINED, IFD_TYPE_EXIF);
        reg(0xA40C, TAG_SUBJECT_DISTANCE_RANGE, TIFF_USHORT, IFD_TYPE_EXIF);
        reg(0xA420, TAG_IMAGE_UNIQUE_ID, TIFF_ASCII, IFD_TYPE_EXIF);
        reg(0x0001, TAG_INTEROPERABILITY_INDEX, TIFF_ASCII, IFD_TYPE_INTEROP);

        // GPS IFD tags
        reg(0x0000, TAG_GPS_VERSION_ID, TIFF_BYTE, IFD_TYPE_GPS);
        reg(0x0001, TAG_GPS_LATITUDE_REF, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x0002, TAG_GPS_LATITUDE, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x0003, TAG_GPS_LONGITUDE_REF, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x0004, TAG_GPS_LONGITUDE, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x0005, TAG_GPS_ALTITUDE_REF, TIFF_BYTE, IFD_TYPE_GPS);
        reg(0x0006, TAG_GPS_ALTITUDE, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x0007, TAG_GPS_TIMESTAMP, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x0008, TAG_GPS_SATELLITES, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x0009, TAG_GPS_STATUS, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x000A, TAG_GPS_MEASURE_MODE, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x000B, TAG_GPS_DOP, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x000C, TAG_GPS_SPEED_REF, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x000D, TAG_GPS_SPEED, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x000E, TAG_GPS_TRACK_REF, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x000F, TAG_GPS_TRACK, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x0010, TAG_GPS_IMG_DIRECTION_REF, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x0011, TAG_GPS_IMG_DIRECTION, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x0012, TAG_GPS_MAP_DATUM, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x0013, TAG_GPS_DEST_LATITUDE_REF, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x0014, TAG_GPS_DEST_LATITUDE, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x0015, TAG_GPS_DEST_LONGITUDE_REF, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x0016, TAG_GPS_DEST_LONGITUDE, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x0017, TAG_GPS_DEST_BEARING_REF, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x0018, TAG_GPS_DEST_BEARING, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x0019, TAG_GPS_DEST_DISTANCE_REF, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x001A, TAG_GPS_DEST_DISTANCE, TIFF_URATIONAL, IFD_TYPE_GPS);
        reg(0x001B, TAG_GPS_PROCESSING_METHOD, TIFF_UNDEFINED, IFD_TYPE_GPS);
        reg(0x001C, TAG_GPS_AREA_INFORMATION, TIFF_UNDEFINED, IFD_TYPE_GPS);
        reg(0x001D, TAG_GPS_DATESTAMP, TIFF_ASCII, IFD_TYPE_GPS);
        reg(0x001E, TAG_GPS_DIFFERENTIAL, TIFF_USHORT, IFD_TYPE_GPS);
    }

    private static final class EntryValue {
        final String stringValue;
        final byte[] rawBytes;
        final long streamOffset;

        EntryValue(String stringValue, byte[] rawBytes, long streamOffset) {
            this.stringValue = stringValue;
            this.rawBytes = rawBytes;
            this.streamOffset = streamOffset;
        }
    }

    private final Map<String, EntryValue> mAttributes = new LinkedHashMap<String, EntryValue>();
    private File mBackingFile;
    private byte[] mOriginalImageBytes;
    private byte[] mThumbnailBytes;
    private long mThumbnailOffset = -1L;
    private long mThumbnailLength = -1L;
    private boolean mThumbnailCompressed;

    public ExifInterface(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("file cannot be null");
        }
        mBackingFile = file;
        try (FileInputStream in = new FileInputStream(file)) {
            loadFromBytes(readAll(in), STREAM_TYPE_FULL_IMAGE_DATA);
        }
    }

    public ExifInterface(String filename) throws IOException {
        if (filename == null) {
            throw new NullPointerException("filename cannot be null");
        }
        File f = new File(filename);
        mBackingFile = f;
        try (FileInputStream in = new FileInputStream(f)) {
            loadFromBytes(readAll(in), STREAM_TYPE_FULL_IMAGE_DATA);
        }
    }

    public ExifInterface(FileDescriptor fileDescriptor) throws IOException {
        if (fileDescriptor == null) {
            throw new NullPointerException("fileDescriptor cannot be null");
        }
        long size = NativeFd.size(fileDescriptor);
        if (size < 0 || size > 64L * 1024 * 1024) {
            throw new IOException("Unsupported ExifInterface file descriptor size: " + size);
        }
        byte[] bytes = NativeFd.readRegion(fileDescriptor, 0, size);
        loadFromBytes(bytes, STREAM_TYPE_FULL_IMAGE_DATA);
    }

    public ExifInterface(InputStream inputStream) throws IOException {
        this(inputStream, STREAM_TYPE_FULL_IMAGE_DATA);
    }

    public ExifInterface(InputStream inputStream, int streamType) throws IOException {
        if (inputStream == null) {
            throw new NullPointerException("inputStream cannot be null");
        }
        if (streamType != STREAM_TYPE_FULL_IMAGE_DATA && streamType != STREAM_TYPE_EXIF_DATA_ONLY) {
            throw new IllegalArgumentException("Invalid streamType: " + streamType);
        }
        loadFromBytes(readAll(inputStream), streamType);
    }

    public static boolean isSupportedMimeType(String mimeType) {
        if (mimeType == null) {
            throw new NullPointerException("mimeType shouldn't be null");
        }
        return SUPPORTED_MIMES.contains(mimeType.toLowerCase(Locale.ROOT));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > 64 * 1024 * 1024) {
                throw new IOException("Image stream exceeds 64 MiB limit");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private void loadFromBytes(byte[] data, int streamType) throws IOException {
        mOriginalImageBytes = data;
        if (data.length < 4) {
            return;
        }
        if (streamType == STREAM_TYPE_EXIF_DATA_ONLY) {
            int offset = 0;
            if (data.length >= 6 && data[0] == 'E' && data[1] == 'x' && data[2] == 'i' && data[3] == 'f'
                    && data[4] == 0 && data[5] == 0) {
                offset = 6;
            }
            parseTiff(data, offset, data.length - offset);
            return;
        }
        // Check JPEG SOI 0xFFD8
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            parseJpeg(data);
            return;
        }
        // Check direct TIFF ("II\x2A\x00" or "MM\x00\x2A")
        if (isTiffHeader(data, 0, data.length)) {
            parseTiff(data, 0, data.length);
            return;
        }
        // Check PNG signature
        if (data.length >= 24 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            parsePng(data);
        }
    }

    private void parseJpeg(byte[] data) {
        int pos = 2;
        int sofWidth = -1;
        int sofHeight = -1;
        while (pos + 4 <= data.length) {
            if ((data[pos] & 0xFF) != 0xFF) {
                pos++;
                continue;
            }
            while (pos < data.length && (data[pos] & 0xFF) == 0xFF) {
                pos++;
            }
            if (pos >= data.length) {
                break;
            }
            int marker = data[pos++] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) { // EOI or SOS
                break;
            }
            if (marker == 0x00 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
                continue;
            }
            if (pos + 2 > data.length) {
                break;
            }
            int segLen = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            if (segLen < 2 || pos + segLen > data.length) {
                break;
            }
            int payloadPos = pos + 2;
            int payloadLen = segLen - 2;
            if (marker == 0xE1) {
                if (payloadLen >= 6
                        && data[payloadPos] == 'E'
                        && data[payloadPos + 1] == 'x'
                        && data[payloadPos + 2] == 'i'
                        && data[payloadPos + 3] == 'f'
                        && data[payloadPos + 4] == 0
                        && data[payloadPos + 5] == 0) {
                    parseTiff(data, payloadPos + 6, payloadLen - 6);
                } else {
                    byte[] xmpPrefix = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);
                    if (payloadLen > xmpPrefix.length && startsWith(data, payloadPos, xmpPrefix)) {
                        int xmpStart = payloadPos + xmpPrefix.length;
                        int xmpLen = payloadLen - xmpPrefix.length;
                        byte[] xmp = new byte[xmpLen];
                        System.arraycopy(data, xmpStart, xmp, 0, xmpLen);
                        mAttributes.put(TAG_XMP, new EntryValue(
                                new String(xmp, StandardCharsets.UTF_8), xmp, xmpStart));
                    }
                }
            } else if ((marker >= 0xC0 && marker <= 0xCF)
                    && marker != 0xC4 && marker != 0xC8 && marker != 0xCC && payloadLen >= 5) {
                sofHeight = ((data[payloadPos + 1] & 0xFF) << 8) | (data[payloadPos + 2] & 0xFF);
                sofWidth = ((data[payloadPos + 3] & 0xFF) << 8) | (data[payloadPos + 4] & 0xFF);
            }
            pos += segLen;
        }
        if (sofWidth > 0 && !mAttributes.containsKey(TAG_IMAGE_WIDTH)) {
            putSyntheticInt(TAG_IMAGE_WIDTH, sofWidth);
        }
        if (sofHeight > 0 && !mAttributes.containsKey(TAG_IMAGE_LENGTH)) {
            putSyntheticInt(TAG_IMAGE_LENGTH, sofHeight);
        }
    }

    private void parsePng(byte[] data) {
        int pos = 8;
        while (pos + 12 <= data.length) {
            int len = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            if (len < 0 || pos + 12L + len > data.length) {
                break;
            }
            String chunkType = new String(data, pos + 4, 4, StandardCharsets.US_ASCII);
            int chunkData = pos + 8;
            if ("IHDR".equals(chunkType) && len >= 8) {
                int w = ByteBuffer.wrap(data, chunkData, 4).order(ByteOrder.BIG_ENDIAN).getInt();
                int h = ByteBuffer.wrap(data, chunkData + 4, 4).order(ByteOrder.BIG_ENDIAN).getInt();
                if (!mAttributes.containsKey(TAG_IMAGE_WIDTH)) {
                    putSyntheticInt(TAG_IMAGE_WIDTH, w);
                }
                if (!mAttributes.containsKey(TAG_IMAGE_LENGTH)) {
                    putSyntheticInt(TAG_IMAGE_LENGTH, h);
                }
            } else if ("eXIf".equals(chunkType) && len >= 8) {
                parseTiff(data, chunkData, len);
            }
            pos += 12 + len;
        }
    }

    private void putSyntheticInt(String tag, int value) {
        String s = Integer.toString(value);
        mAttributes.put(tag, new EntryValue(s, s.getBytes(StandardCharsets.US_ASCII), -1L));
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (offset + prefix.length > data.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTiffHeader(byte[] data, int offset, int length) {
        if (length < 8 || offset + 8 > data.length) {
            return false;
        }
        boolean little = data[offset] == 'I' && data[offset + 1] == 'I';
        boolean big = data[offset] == 'M' && data[offset + 1] == 'M';
        if (!little && !big) {
            return false;
        }
        ByteOrder order = little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        int magic = ByteBuffer.wrap(data, offset + 2, 2).order(order).getShort() & 0xFFFF;
        return magic == 0x002A || magic == 0x0055 || magic == 0x4F52;
    }

    private void parseTiff(byte[] data, int tiffStart, int tiffLength) {
        if (!isTiffHeader(data, tiffStart, tiffLength)) {
            return;
        }
        ByteOrder order = (data[tiffStart] == 'I') ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        ByteBuffer buf = ByteBuffer.wrap(data, tiffStart, tiffLength).slice();
        buf.order(order);
        int ifd0Offset = buf.getInt(4);
        Set<Integer> visited = new HashSet<Integer>();
        int nextIfd = parseIfd(buf, data, tiffStart, ifd0Offset, IFD_TYPE_PRIMARY, visited);
        if (nextIfd > 0 && nextIfd < tiffLength) {
            parseIfd(buf, data, tiffStart, nextIfd, IFD_TYPE_THUMBNAIL, visited);
            extractThumbnailIfPresent(data, tiffStart, tiffLength);
        }
    }

    private int parseIfd(ByteBuffer buf, byte[] fullData, int tiffStart, int ifdOffset, int ifdType,
            Set<Integer> visited) {
        if (ifdOffset < 8 || ifdOffset + 2 > buf.limit() || !visited.add(Integer.valueOf(ifdOffset))) {
            return 0;
        }
        int entryCount = buf.getShort(ifdOffset) & 0xFFFF;
        int pos = ifdOffset + 2;
        for (int i = 0; i < entryCount; i++) {
            if (pos + 12 > buf.limit()) {
                return 0;
            }
            int tagNum = buf.getShort(pos) & 0xFFFF;
            int format = buf.getShort(pos + 2) & 0xFFFF;
            int componentCount = buf.getInt(pos + 4);
            if (format >= 1 && format < TYPE_SIZES.length && componentCount >= 0) {
                long byteCountLong = (long) componentCount * TYPE_SIZES[format];
                if (byteCountLong <= buf.limit()) {
                    int byteCount = (int) byteCountLong;
                    int valueOffset;
                    if (byteCount <= 4) {
                        valueOffset = pos + 8;
                    } else {
                        valueOffset = buf.getInt(pos + 8);
                    }
                    if (valueOffset >= 0 && valueOffset + byteCount <= buf.limit()) {
                        if (tagNum == 0x8769 && ifdType == IFD_TYPE_PRIMARY && byteCount >= 4) {
                            int exifOffset = (format == TIFF_USHORT)
                                    ? (buf.getShort(valueOffset) & 0xFFFF)
                                    : buf.getInt(valueOffset);
                            parseIfd(buf, fullData, tiffStart, exifOffset, IFD_TYPE_EXIF, visited);
                        } else if (tagNum == 0x8825 && ifdType == IFD_TYPE_PRIMARY && byteCount >= 4) {
                            int gpsOffset = (format == TIFF_USHORT)
                                    ? (buf.getShort(valueOffset) & 0xFFFF)
                                    : buf.getInt(valueOffset);
                            parseIfd(buf, fullData, tiffStart, gpsOffset, IFD_TYPE_GPS, visited);
                        } else if (tagNum == 0xA005 && ifdType == IFD_TYPE_EXIF && byteCount >= 4) {
                            int interopOffset = (format == TIFF_USHORT)
                                    ? (buf.getShort(valueOffset) & 0xFFFF)
                                    : buf.getInt(valueOffset);
                            parseIfd(buf, fullData, tiffStart, interopOffset, IFD_TYPE_INTEROP, visited);
                        } else {
                            String tagName = (ifdType == IFD_TYPE_GPS)
                                    ? GPS_TAGS.get(Integer.valueOf(tagNum))
                                    : PRIMARY_EXIF_TAGS.get(Integer.valueOf(tagNum));
                            if (tagName != null) {
                                if (ifdType == IFD_TYPE_THUMBNAIL && TAG_ORIENTATION.equals(tagName)) {
                                    tagName = TAG_THUMBNAIL_ORIENTATION;
                                } else if (ifdType == IFD_TYPE_THUMBNAIL && TAG_IMAGE_WIDTH.equals(tagName)) {
                                    tagName = TAG_THUMBNAIL_IMAGE_WIDTH;
                                } else if (ifdType == IFD_TYPE_THUMBNAIL && TAG_IMAGE_LENGTH.equals(tagName)) {
                                    tagName = TAG_THUMBNAIL_IMAGE_LENGTH;
                                }
                                byte[] raw = new byte[byteCount];
                                System.arraycopy(fullData, tiffStart + valueOffset, raw, 0, byteCount);
                                String decoded = decodeValue(buf, valueOffset, format, componentCount, raw);
                                if (decoded != null) {
                                    mAttributes.put(tagName, new EntryValue(decoded, raw, tiffStart + valueOffset));
                                }
                            }
                        }
                    }
                }
            }
            pos += 12;
        }
        if (pos + 4 <= buf.limit()) {
            return buf.getInt(pos);
        }
        return 0;
    }

    private void extractThumbnailIfPresent(byte[] fullData, int tiffStart, int tiffLength) {
        int jpegOffset = getAttributeInt(TAG_JPEG_INTERCHANGE_FORMAT, -1);
        int jpegLen = getAttributeInt(TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, -1);
        if (jpegOffset >= 0 && jpegLen > 0 && jpegOffset + jpegLen <= tiffLength) {
            mThumbnailOffset = tiffStart + jpegOffset;
            mThumbnailLength = jpegLen;
            mThumbnailBytes = new byte[jpegLen];
            System.arraycopy(fullData, (int) mThumbnailOffset, mThumbnailBytes, 0, jpegLen);
            mThumbnailCompressed = true;
        }
    }

    private static String decodeValue(ByteBuffer buf, int offset, int format, int count, byte[] raw) {
        if (format == TIFF_ASCII) {
            int end = raw.length;
            while (end > 0 && raw[end - 1] == 0) {
                end--;
            }
            return new String(raw, 0, end, StandardCharsets.UTF_8);
        }
        if (format == TIFF_UNDEFINED || (format == TIFF_BYTE && count > 4)) {
            if (raw.length >= 8 && startsWith(raw, 0, "ASCII\0\0\0".getBytes(StandardCharsets.US_ASCII))) {
                return new String(raw, 8, raw.length - 8, StandardCharsets.UTF_8).trim();
            }
            return new String(raw, StandardCharsets.ISO_8859_1);
        }
        if (count == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int step = TYPE_SIZES[format];
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            int p = offset + i * step;
            switch (format) {
                case TIFF_BYTE:
                    sb.append(buf.get(p) & 0xFF);
                    break;
                case TIFF_SBYTE:
                    sb.append((int) buf.get(p));
                    break;
                case TIFF_USHORT:
                    sb.append(buf.getShort(p) & 0xFFFF);
                    break;
                case TIFF_SSHORT:
                    sb.append((int) buf.getShort(p));
                    break;
                case TIFF_ULONG:
                    sb.append(buf.getInt(p) & 0xFFFFFFFFL);
                    break;
                case TIFF_SLONG:
                    sb.append(buf.getInt(p));
                    break;
                case TIFF_URATIONAL: {
                    long num = buf.getInt(p) & 0xFFFFFFFFL;
                    long den = buf.getInt(p + 4) & 0xFFFFFFFFL;
                    sb.append(num).append('/').append(den);
                    break;
                }
                case TIFF_SRATIONAL: {
                    int num = buf.getInt(p);
                    int den = buf.getInt(p + 4);
                    sb.append(num).append('/').append(den);
                    break;
                }
                case TIFF_SINGLE:
                    sb.append(buf.getFloat(p));
                    break;
                case TIFF_DOUBLE:
                    sb.append(buf.getDouble(p));
                    break;
                default:
                    break;
            }
        }
        return sb.toString();
    }

    public String getAttribute(String tag) {
        if (tag == null) {
            throw new NullPointerException("tag shouldn't be null");
        }
        EntryValue entry = mAttributes.get(tag);
        return entry != null ? entry.stringValue : null;
    }

    public int getAttributeInt(String tag, int defaultValue) {
        String val = getAttribute(tag);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        try {
            int comma = val.indexOf(',');
            String token = (comma >= 0 ? val.substring(0, comma) : val).trim();
            int slash = token.indexOf('/');
            if (slash >= 0) {
                double num = Double.parseDouble(token.substring(0, slash));
                double den = Double.parseDouble(token.substring(slash + 1));
                return den == 0.0 ? defaultValue : (int) (num / den);
            }
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getAttributeDouble(String tag, double defaultValue) {
        String val = getAttribute(tag);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        try {
            int comma = val.indexOf(',');
            String token = (comma >= 0 ? val.substring(0, comma) : val).trim();
            int slash = token.indexOf('/');
            if (slash >= 0) {
                double num = Double.parseDouble(token.substring(0, slash));
                double den = Double.parseDouble(token.substring(slash + 1));
                return den == 0.0 ? defaultValue : (num / den);
            }
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void setAttribute(String tag, String value) {
        if (tag == null) {
            throw new NullPointerException("tag shouldn't be null");
        }
        if (value == null) {
            mAttributes.remove(tag);
        } else {
            byte[] raw = value.getBytes(StandardCharsets.UTF_8);
            mAttributes.put(tag, new EntryValue(value, raw, -1L));
        }
    }

    public boolean hasAttribute(String tag) {
        if (tag == null) {
            throw new NullPointerException("tag shouldn't be null");
        }
        return mAttributes.containsKey(tag);
    }

    public byte[] getAttributeBytes(String tag) {
        if (tag == null) {
            throw new NullPointerException("tag shouldn't be null");
        }
        EntryValue entry = mAttributes.get(tag);
        return (entry != null && entry.rawBytes != null) ? entry.rawBytes.clone() : null;
    }

    public long[] getAttributeRange(String tag) {
        if (tag == null) {
            throw new NullPointerException("tag shouldn't be null");
        }
        EntryValue entry = mAttributes.get(tag);
        if (entry == null || entry.streamOffset < 0 || entry.rawBytes == null) {
            return null;
        }
        return new long[]{entry.streamOffset, entry.rawBytes.length};
    }

    public boolean hasThumbnail() {
        return mThumbnailBytes != null && mThumbnailBytes.length > 0;
    }

    public byte[] getThumbnail() {
        return getThumbnailBytes();
    }

    public byte[] getThumbnailBytes() {
        return mThumbnailBytes != null ? mThumbnailBytes.clone() : null;
    }

    public Bitmap getThumbnailBitmap() {
        if (mThumbnailBytes == null || mThumbnailBytes.length == 0) {
            return null;
        }
        return BitmapFactory.decodeByteArray(mThumbnailBytes, 0, mThumbnailBytes.length);
    }

    public boolean isThumbnailCompressed() {
        return mThumbnailCompressed;
    }

    public long[] getThumbnailRange() {
        if (mThumbnailOffset < 0 || mThumbnailLength <= 0) {
            return null;
        }
        return new long[]{mThumbnailOffset, mThumbnailLength};
    }

    public boolean getLatLong(float[] output) {
        if (output == null || output.length < 2) {
            throw new IllegalArgumentException("output array must have length >= 2");
        }
        String latValue = getAttribute(TAG_GPS_LATITUDE);
        String latRef = getAttribute(TAG_GPS_LATITUDE_REF);
        String lngValue = getAttribute(TAG_GPS_LONGITUDE);
        String lngRef = getAttribute(TAG_GPS_LONGITUDE_REF);
        if (latValue == null || latRef == null || lngValue == null || lngRef == null) {
            return false;
        }
        try {
            output[0] = convertRationalLatLonToFloat(latValue, latRef);
            output[1] = convertRationalLatLonToFloat(lngValue, lngRef);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static float convertRationalLatLonToFloat(String rationalString, String ref) {
        String[] parts = rationalString.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid DMS rational: " + rationalString);
        }
        double degrees = parseRational(parts[0]);
        double minutes = parseRational(parts[1]);
        double seconds = parseRational(parts[2]);
        double result = degrees + (minutes / 60.0) + (seconds / 3600.0);
        if ("S".equalsIgnoreCase(ref.trim()) || "W".equalsIgnoreCase(ref.trim())) {
            return (float) -result;
        }
        return (float) result;
    }

    private static double parseRational(String part) {
        String trimmed = part.trim();
        int slash = trimmed.indexOf('/');
        if (slash < 0) {
            return Double.parseDouble(trimmed);
        }
        double num = Double.parseDouble(trimmed.substring(0, slash));
        double den = Double.parseDouble(trimmed.substring(slash + 1));
        if (den == 0.0) {
            throw new IllegalArgumentException("Zero denominator in rational");
        }
        return num / den;
    }

    public double getAltitude(double defaultValue) {
        double altitude = getAttributeDouble(TAG_GPS_ALTITUDE, -1.0);
        int ref = getAttributeInt(TAG_GPS_ALTITUDE_REF, -1);
        if (altitude >= 0.0 && ref >= 0) {
            return altitude * (ref == 1 ? -1.0 : 1.0);
        }
        return defaultValue;
    }

    public long getDateTime() {
        return parseDateTime(getAttribute(TAG_DATETIME), getAttribute(TAG_SUBSEC_TIME),
                getAttribute(TAG_OFFSET_TIME));
    }

    public long getDateTimeDigitized() {
        return parseDateTime(getAttribute(TAG_DATETIME_DIGITIZED), getAttribute(TAG_SUBSEC_TIME_DIGITIZED),
                getAttribute(TAG_OFFSET_TIME_DIGITIZED));
    }

    public long getDateTimeOriginal() {
        return parseDateTime(getAttribute(TAG_DATETIME_ORIGINAL), getAttribute(TAG_SUBSEC_TIME_ORIGINAL),
                getAttribute(TAG_OFFSET_TIME_ORIGINAL));
    }

    public long getGpsDateTime() {
        String date = getAttribute(TAG_GPS_DATESTAMP);
        String time = getAttribute(TAG_GPS_TIMESTAMP);
        if (date == null || time == null) {
            return -1L;
        }
        try {
            String[] hms = time.split(",");
            if (hms.length < 3) {
                return -1L;
            }
            int h = (int) parseRational(hms[0]);
            int m = (int) parseRational(hms[1]);
            double secDouble = parseRational(hms[2]);
            int s = (int) secDouble;
            int ms = (int) Math.round((secDouble - s) * 1000.0);
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = fmt.parse(
                    String.format(Locale.US, "%s %02d:%02d:%02d", date.trim(), h, m, s),
                    new ParsePosition(0));
            return parsed == null ? -1L : parsed.getTime() + ms;
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    private static long parseDateTime(String dateTimeString, String subSecs, String offsetString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return -1L;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
        if (offsetString != null && !offsetString.trim().isEmpty()) {
            fmt.setTimeZone(TimeZone.getTimeZone("GMT" + offsetString.trim()));
        } else {
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        Date datetime = fmt.parse(dateTimeString.trim(), new ParsePosition(0));
        if (datetime == null) {
            return -1L;
        }
        long msecs = datetime.getTime();
        if (subSecs != null && !subSecs.trim().isEmpty()) {
            try {
                String digits = subSecs.trim();
                if (digits.length() > 3) {
                    digits = digits.substring(0, 3);
                }
                while (digits.length() < 3) {
                    digits = digits + "0";
                }
                msecs += Long.parseLong(digits);
            } catch (NumberFormatException ignored) {
            }
        }
        return msecs;
    }

    public void saveAttributes() throws IOException {
        if (mBackingFile == null) {
            throw new IOException("ExifInterface must be constructed from a File or filename to saveAttributes()");
        }
        if (mOriginalImageBytes == null || mOriginalImageBytes.length < 2
                || (mOriginalImageBytes[0] & 0xFF) != 0xFF
                || (mOriginalImageBytes[1] & 0xFF) != 0xD8) {
            throw new IOException("Saving Exif attributes is only supported for JPEG files");
        }
        byte[] tiffBytes = buildTiffBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeShort(0xFFD8);
        int app1Len = 2 + 6 + tiffBytes.length;
        if (app1Len > 0xFFFF) {
            throw new IOException("EXIF APP1 segment exceeds 64 KiB");
        }
        dos.writeShort(0xFFE1);
        dos.writeShort(app1Len);
        dos.writeBytes("Exif");
        dos.writeByte(0);
        dos.writeByte(0);
        dos.write(tiffBytes);

        // Copy all remaining non-Exif APP1 segments + image scan data
        int pos = 2;
        while (pos + 2 <= mOriginalImageBytes.length) {
            if ((mOriginalImageBytes[pos] & 0xFF) != 0xFF) {
                dos.write(mOriginalImageBytes, pos, mOriginalImageBytes.length - pos);
                break;
            }
            int marker = mOriginalImageBytes[pos + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                dos.write(mOriginalImageBytes, pos, mOriginalImageBytes.length - pos);
                break;
            }
            if (pos + 4 > mOriginalImageBytes.length) {
                dos.write(mOriginalImageBytes, pos, mOriginalImageBytes.length - pos);
                break;
            }
            int segLen = ((mOriginalImageBytes[pos + 2] & 0xFF) << 8) | (mOriginalImageBytes[pos + 3] & 0xFF);
            if (segLen < 2 || pos + 2 + segLen > mOriginalImageBytes.length) {
                dos.write(mOriginalImageBytes, pos, mOriginalImageBytes.length - pos);
                break;
            }
            boolean isExifApp1 = (marker == 0xE1 && segLen >= 8
                    && mOriginalImageBytes[pos + 4] == 'E'
                    && mOriginalImageBytes[pos + 5] == 'x'
                    && mOriginalImageBytes[pos + 6] == 'i'
                    && mOriginalImageBytes[pos + 7] == 'f');
            if (!isExifApp1) {
                dos.write(mOriginalImageBytes, pos, 2 + segLen);
            }
            pos += 2 + segLen;
        }
        dos.flush();
        byte[] updated = out.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(mBackingFile)) {
            fos.write(updated);
        }
        mOriginalImageBytes = updated;
    }

    private byte[] buildTiffBytes() throws IOException {
        Map<Integer, byte[]> ifd0Entries = new LinkedHashMap<Integer, byte[]>();
        Map<Integer, Integer> ifd0Formats = new HashMap<Integer, Integer>();
        Map<Integer, Integer> ifd0Counts = new HashMap<Integer, Integer>();

        for (Map.Entry<String, EntryValue> e : mAttributes.entrySet()) {
            TagSpec spec = TAG_SPECS.get(e.getKey());
            if (spec == null || (spec.ifd != IFD_TYPE_PRIMARY && spec.ifd != IFD_TYPE_EXIF)) {
                continue;
            }
            String val = e.getValue().stringValue;
            if (val == null) {
                continue;
            }
            Integer key = Integer.valueOf(spec.number);
            if (spec.format == TIFF_ASCII) {
                byte[] ascii = (val + "\0").getBytes(StandardCharsets.UTF_8);
                ifd0Entries.put(key, ascii);
                ifd0Formats.put(key, Integer.valueOf(TIFF_ASCII));
                ifd0Counts.put(key, Integer.valueOf(ascii.length));
            } else if (spec.format == TIFF_USHORT) {
                int v = getAttributeInt(e.getKey(), 0);
                ByteBuffer b = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN);
                b.putShort((short) v);
                ifd0Entries.put(key, b.array());
                ifd0Formats.put(key, Integer.valueOf(TIFF_USHORT));
                ifd0Counts.put(key, Integer.valueOf(1));
            } else if (spec.format == TIFF_ULONG) {
                int v = getAttributeInt(e.getKey(), 0);
                ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
                b.putInt(v);
                ifd0Entries.put(key, b.array());
                ifd0Formats.put(key, Integer.valueOf(TIFF_ULONG));
                ifd0Counts.put(key, Integer.valueOf(1));
            } else if (spec.format == TIFF_URATIONAL) {
                int slash = val.indexOf('/');
                long num = 0;
                long den = 1;
                try {
                    if (slash >= 0) {
                        num = Long.parseLong(val.substring(0, slash).trim());
                        den = Long.parseLong(val.substring(slash + 1).trim());
                    } else {
                        num = Math.round(Double.parseDouble(val) * 1000.0);
                        den = 1000L;
                    }
                } catch (NumberFormatException ignored) {
                }
                ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
                b.putInt((int) num);
                b.putInt((int) den);
                ifd0Entries.put(key, b.array());
                ifd0Formats.put(key, Integer.valueOf(TIFF_URATIONAL));
                ifd0Counts.put(key, Integer.valueOf(1));
            }
        }

        int count = ifd0Entries.size();
        int dataAreaOffset = 8 + 2 + count * 12 + 4;
        ByteArrayOutputStream extra = new ByteArrayOutputStream();
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        DataOutputStream td = new DataOutputStream(tiff);
        td.writeByte('M');
        td.writeByte('M');
        td.writeShort(0x002A);
        td.writeInt(8);
        td.writeShort(count);
        for (Map.Entry<Integer, byte[]> entry : ifd0Entries.entrySet()) {
            int tagNum = entry.getKey().intValue();
            byte[] bytes = entry.getValue();
            int fmt = ifd0Formats.get(entry.getKey()).intValue();
            int compCount = ifd0Counts.get(entry.getKey()).intValue();
            td.writeShort(tagNum);
            td.writeShort(fmt);
            td.writeInt(compCount);
            if (bytes.length <= 4) {
                td.write(bytes);
                for (int pad = bytes.length; pad < 4; pad++) {
                    td.writeByte(0);
                }
            } else {
                td.writeInt(dataAreaOffset + extra.size());
                extra.write(bytes);
                if ((bytes.length & 1) != 0) {
                    extra.write(0);
                }
            }
        }
        td.writeInt(0);
        td.write(extra.toByteArray());
        td.flush();
        return tiff.toByteArray();
    }
}
