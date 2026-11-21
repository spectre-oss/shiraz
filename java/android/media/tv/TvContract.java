// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.BaseColumns;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TvContract {
    public static final String AUTHORITY = "android.media.tv";

    public static final String ACTION_INITIALIZE_PROGRAMS =
            "android.media.tv.action.INITIALIZE_PROGRAMS";
    public static final String ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED =
            "android.media.tv.action.PREVIEW_PROGRAM_BROWSABLE_DISABLED";
    public static final String ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED =
            "android.media.tv.action.WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED";
    public static final String ACTION_PREVIEW_PROGRAM_ADDED_TO_WATCH_NEXT =
            "android.media.tv.action.PREVIEW_PROGRAM_ADDED_TO_WATCH_NEXT";
    public static final String ACTION_REQUEST_CHANNEL_BROWSABLE =
            "android.media.tv.action.REQUEST_CHANNEL_BROWSABLE";

    public static final String EXTRA_CHANNEL_ID = "android.media.tv.extra.CHANNEL_ID";
    public static final String EXTRA_PREVIEW_PROGRAM_ID =
            "android.media.tv.extra.PREVIEW_PROGRAM_ID";
    public static final String EXTRA_WATCH_NEXT_PROGRAM_ID =
            "android.media.tv.extra.WATCH_NEXT_PROGRAM_ID";

    private static final String PATH_CHANNEL = "channel";
    private static final String PATH_PROGRAM = "program";
    private static final String PATH_RECORDED_PROGRAM = "recorded_program";
    private static final String PATH_PREVIEW_PROGRAM = "preview_program";
    private static final String PATH_WATCH_NEXT_PROGRAM = "watch_next_program";
    private static final String PATH_PASSTHROUGH = "passthrough";

    private static final String PARAM_INPUT = "input";
    private static final String PARAM_CHANNEL = "channel";
    private static final String PARAM_START_TIME = "start_time";
    private static final String PARAM_END_TIME = "end_time";

    private TvContract() {
    }

    public interface BaseTvColumns extends BaseColumns {
        String COLUMN_PACKAGE_NAME = "package_name";
    }

    public static final class Channels implements BaseTvColumns {
        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/" + PATH_CHANNEL);

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/channel";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/channel";

        public static final String TYPE_OTHER = "TYPE_OTHER";
        public static final String TYPE_NTSC = "TYPE_NTSC";
        public static final String TYPE_PAL = "TYPE_PAL";
        public static final String TYPE_SECAM = "TYPE_SECAM";
        public static final String TYPE_DVB_T = "TYPE_DVB_T";
        public static final String TYPE_DVB_T2 = "TYPE_DVB_T2";
        public static final String TYPE_DVB_S = "TYPE_DVB_S";
        public static final String TYPE_DVB_S2 = "TYPE_DVB_S2";
        public static final String TYPE_DVB_C = "TYPE_DVB_C";
        public static final String TYPE_DVB_C2 = "TYPE_DVB_C2";
        public static final String TYPE_DVB_H = "TYPE_DVB_H";
        public static final String TYPE_DVB_SH = "TYPE_DVB_SH";
        public static final String TYPE_ATSC_T = "TYPE_ATSC_T";
        public static final String TYPE_ATSC3_T = "TYPE_ATSC3_T";
        public static final String TYPE_ATSC_C = "TYPE_ATSC_C";
        public static final String TYPE_ATSC_M_H = "TYPE_ATSC_M_H";
        public static final String TYPE_ISDB_T = "TYPE_ISDB_T";
        public static final String TYPE_ISDB_TB = "TYPE_ISDB_TB";
        public static final String TYPE_ISDB_S = "TYPE_ISDB_S";
        public static final String TYPE_ISDB_S3 = "TYPE_ISDB_S3";
        public static final String TYPE_ISDB_C = "TYPE_ISDB_C";
        public static final String TYPE_1SEG = "TYPE_1SEG";
        public static final String TYPE_DTMB = "TYPE_DTMB";
        public static final String TYPE_CMMB = "TYPE_CMMB";
        public static final String TYPE_T_DMB = "TYPE_T_DMB";
        public static final String TYPE_S_DMB = "TYPE_S_DMB";
        public static final String TYPE_PREVIEW = "TYPE_PREVIEW";

        public static final String SERVICE_TYPE_OTHER = "SERVICE_TYPE_OTHER";
        public static final String SERVICE_TYPE_AUDIO_VIDEO = "SERVICE_TYPE_AUDIO_VIDEO";
        public static final String SERVICE_TYPE_AUDIO = "SERVICE_TYPE_AUDIO";

        public static final String VIDEO_FORMAT_240P = "VIDEO_FORMAT_240P";
        public static final String VIDEO_FORMAT_360P = "VIDEO_FORMAT_360P";
        public static final String VIDEO_FORMAT_480I = "VIDEO_FORMAT_480I";
        public static final String VIDEO_FORMAT_480P = "VIDEO_FORMAT_480P";
        public static final String VIDEO_FORMAT_576I = "VIDEO_FORMAT_576I";
        public static final String VIDEO_FORMAT_576P = "VIDEO_FORMAT_576P";
        public static final String VIDEO_FORMAT_720P = "VIDEO_FORMAT_720P";
        public static final String VIDEO_FORMAT_1080I = "VIDEO_FORMAT_1080I";
        public static final String VIDEO_FORMAT_1080P = "VIDEO_FORMAT_1080P";
        public static final String VIDEO_FORMAT_2160P = "VIDEO_FORMAT_2160P";
        public static final String VIDEO_FORMAT_4320P = "VIDEO_FORMAT_4320P";

        public static final String VIDEO_RESOLUTION_SD = "VIDEO_RESOLUTION_SD";
        public static final String VIDEO_RESOLUTION_ED = "VIDEO_RESOLUTION_ED";
        public static final String VIDEO_RESOLUTION_HD = "VIDEO_RESOLUTION_HD";
        public static final String VIDEO_RESOLUTION_FHD = "VIDEO_RESOLUTION_FHD";
        public static final String VIDEO_RESOLUTION_UHD = "VIDEO_RESOLUTION_UHD";

        public static final String COLUMN_INPUT_ID = "input_id";
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_SERVICE_TYPE = "service_type";
        public static final String COLUMN_ORIGINAL_NETWORK_ID = "original_network_id";
        public static final String COLUMN_TRANSPORT_STREAM_ID = "transport_stream_id";
        public static final String COLUMN_SERVICE_ID = "service_id";
        public static final String COLUMN_DISPLAY_NUMBER = "display_number";
        public static final String COLUMN_DISPLAY_NAME = "display_name";
        public static final String COLUMN_NETWORK_AFFILIATION = "network_affiliation";
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_VIDEO_FORMAT = "video_format";
        public static final String COLUMN_VIDEO_RESOLUTION = "video_resolution";
        public static final String COLUMN_BROWSABLE = "browsable";
        public static final String COLUMN_SEARCHABLE = "searchable";
        public static final String COLUMN_LOCKED = "locked";
        public static final String COLUMN_APP_LINK_ICON_URI = "app_link_icon_uri";
        public static final String COLUMN_APP_LINK_POSTER_ART_URI = "app_link_poster_art_uri";
        public static final String COLUMN_APP_LINK_TEXT = "app_link_text";
        public static final String COLUMN_APP_LINK_COLOR = "app_link_color";
        public static final String COLUMN_APP_LINK_INTENT_URI = "app_link_intent_uri";
        public static final String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id";
        public static final String COLUMN_INTERNAL_PROVIDER_DATA = "internal_provider_data";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG1 = "internal_provider_flag1";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG2 = "internal_provider_flag2";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG3 = "internal_provider_flag3";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG4 = "internal_provider_flag4";
        public static final String COLUMN_VERSION_NUMBER = "version_number";
        public static final String COLUMN_TRANSIENT = "transient";
        public static final String COLUMN_GLOBAL_CONTENT_ID = "global_content_id";
        public static final String COLUMN_REMOTE_CONTROL_KEY_PRESET_NUMBER =
                "remote_control_key_preset_number";
        public static final String COLUMN_SCRAMBLED = "scrambled";
        public static final String COLUMN_BROADCAST_GENRE = "broadcast_genre";
        public static final String COLUMN_CHANNEL_LIST_ID = "channel_list_id";

        private static final Map<String, String> VIDEO_FORMAT_TO_RESOLUTION_MAP;

        static {
            Map<String, String> map = new HashMap<String, String>();
            map.put(VIDEO_FORMAT_480I, VIDEO_RESOLUTION_SD);
            map.put(VIDEO_FORMAT_480P, VIDEO_RESOLUTION_ED);
            map.put(VIDEO_FORMAT_576I, VIDEO_RESOLUTION_SD);
            map.put(VIDEO_FORMAT_576P, VIDEO_RESOLUTION_ED);
            map.put(VIDEO_FORMAT_720P, VIDEO_RESOLUTION_HD);
            map.put(VIDEO_FORMAT_1080I, VIDEO_RESOLUTION_HD);
            map.put(VIDEO_FORMAT_1080P, VIDEO_RESOLUTION_FHD);
            map.put(VIDEO_FORMAT_2160P, VIDEO_RESOLUTION_UHD);
            map.put(VIDEO_FORMAT_4320P, VIDEO_RESOLUTION_UHD);
            map.put(VIDEO_FORMAT_240P, VIDEO_RESOLUTION_SD);
            map.put(VIDEO_FORMAT_360P, VIDEO_RESOLUTION_SD);
            VIDEO_FORMAT_TO_RESOLUTION_MAP = Collections.unmodifiableMap(map);
        }

        private Channels() {
        }

        public static String getVideoResolution(String videoFormat) {
            return VIDEO_FORMAT_TO_RESOLUTION_MAP.get(videoFormat);
        }

        public static final class Logo {
            public static final String CONTENT_DIRECTORY = "logo";

            private Logo() {
            }
        }
    }

    public static final class Programs implements BaseTvColumns {
        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/" + PATH_PROGRAM);

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/program";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/program";

        public static final int REVIEW_RATING_STYLE_STARS = 0;
        public static final int REVIEW_RATING_STYLE_THUMBS_UP_DOWN = 1;
        public static final int REVIEW_RATING_STYLE_PERCENTAGE = 2;

        public static final String COLUMN_CHANNEL_ID = "channel_id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_SEASON_DISPLAY_NUMBER = "season_display_number";
        @Deprecated
        public static final String COLUMN_SEASON_NUMBER = "season_number";
        public static final String COLUMN_EPISODE_DISPLAY_NUMBER = "episode_display_number";
        @Deprecated
        public static final String COLUMN_EPISODE_NUMBER = "episode_number";
        public static final String COLUMN_SEASON_TITLE = "season_title";
        public static final String COLUMN_EPISODE_TITLE = "episode_title";
        public static final String COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis";
        public static final String COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis";
        public static final String COLUMN_BROADCAST_GENRE = "broadcast_genre";
        public static final String COLUMN_CANONICAL_GENRE = "canonical_genre";
        public static final String COLUMN_SHORT_DESCRIPTION = "short_description";
        public static final String COLUMN_LONG_DESCRIPTION = "long_description";
        public static final String COLUMN_VIDEO_WIDTH = "video_width";
        public static final String COLUMN_VIDEO_HEIGHT = "video_height";
        public static final String COLUMN_AUDIO_LANGUAGE = "audio_language";
        public static final String COLUMN_CONTENT_RATING = "content_rating";
        public static final String COLUMN_POSTER_ART_URI = "poster_art_uri";
        public static final String COLUMN_THUMBNAIL_URI = "thumbnail_uri";
        public static final String COLUMN_SEARCHABLE = "searchable";
        public static final String COLUMN_RECORDING_PROHIBITED = "recording_prohibited";
        public static final String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id";
        public static final String COLUMN_INTERNAL_PROVIDER_DATA = "internal_provider_data";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG1 = "internal_provider_flag1";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG2 = "internal_provider_flag2";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG3 = "internal_provider_flag3";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG4 = "internal_provider_flag4";
        public static final String COLUMN_REVIEW_RATING_STYLE = "review_rating_style";
        public static final String COLUMN_REVIEW_RATING = "review_rating";
        public static final String COLUMN_VERSION_NUMBER = "version_number";
        public static final String COLUMN_SERIES_ID = "series_id";
        public static final String COLUMN_MULTI_SERIES_ID = "multi_series_id";
        public static final String COLUMN_SPLIT_ID = "split_id";
        public static final String COLUMN_EVENT_ID = "event_id";
        public static final String COLUMN_GLOBAL_CONTENT_ID = "global_content_id";
        public static final String COLUMN_SCRAMBLED = "scrambled";

        private Programs() {
        }

        public static final class Genres {
            public static final String FAMILY_KIDS = "FAMILY_KIDS";
            public static final String SPORTS = "SPORTS";
            public static final String SHOPPING = "SHOPPING";
            public static final String MOVIES = "MOVIES";
            public static final String COMEDY = "COMEDY";
            public static final String TRAVEL = "TRAVEL";
            public static final String DRAMA = "DRAMA";
            public static final String EDUCATION = "EDUCATION";
            public static final String ANIMAL_WILDLIFE = "ANIMAL_WILDLIFE";
            public static final String NEWS = "NEWS";
            public static final String GAMING = "GAMING";
            public static final String ARTS = "ARTS";
            public static final String ENTERTAINMENT = "ENTERTAINMENT";
            public static final String LIFE_STYLE = "LIFE_STYLE";
            public static final String MUSIC = "MUSIC";
            public static final String PREMIER = "PREMIER";
            public static final String TECH_SCIENCE = "TECH_SCIENCE";

            private static final Set<String> CANONICAL_GENRES;

            static {
                Set<String> set = new HashSet<String>();
                set.add(FAMILY_KIDS);
                set.add(SPORTS);
                set.add(SHOPPING);
                set.add(MOVIES);
                set.add(COMEDY);
                set.add(TRAVEL);
                set.add(DRAMA);
                set.add(EDUCATION);
                set.add(ANIMAL_WILDLIFE);
                set.add(NEWS);
                set.add(GAMING);
                set.add(ARTS);
                set.add(ENTERTAINMENT);
                set.add(LIFE_STYLE);
                set.add(MUSIC);
                set.add(PREMIER);
                set.add(TECH_SCIENCE);
                CANONICAL_GENRES = Collections.unmodifiableSet(set);
            }

            private Genres() {
            }

            public static String encode(String... genres) {
                if (genres == null) {
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                String separator = "";
                for (int i = 0; i < genres.length; i++) {
                    String genre = genres[i];
                    if (genre == null) {
                        continue;
                    }
                    sb.append(separator);
                    for (int j = 0; j < genre.length(); j++) {
                        char c = genre.charAt(j);
                        if (c == '"' || c == ',') {
                            sb.append('"');
                        }
                        sb.append(c);
                    }
                    separator = ",";
                }
                return sb.toString();
            }

            public static String[] decode(String genres) {
                Objects.requireNonNull(genres, "genres cannot be null");
                if (genres.trim().isEmpty()) {
                    return new String[0];
                }
                List<String> result = new ArrayList<String>();
                StringBuilder sb = new StringBuilder();
                boolean escape = false;
                for (int i = 0; i < genres.length(); i++) {
                    char c = genres.charAt(i);
                    if (escape) {
                        sb.append(c);
                        escape = false;
                    } else if (c == '"') {
                        escape = true;
                    } else if (c == ',') {
                        String trimmed = sb.toString().trim();
                        if (!trimmed.isEmpty()) {
                            result.add(trimmed);
                        }
                        sb.setLength(0);
                    } else {
                        sb.append(c);
                    }
                }
                String last = sb.toString().trim();
                if (!last.isEmpty()) {
                    result.add(last);
                }
                return result.toArray(new String[0]);
            }

            public static boolean isCanonical(String genre) {
                return CANONICAL_GENRES.contains(genre);
            }
        }
    }

    public static final class RecordedPrograms implements BaseTvColumns {
        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/" + PATH_RECORDED_PROGRAM);

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/recorded_program";
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/recorded_program";

        public static final int REVIEW_RATING_STYLE_STARS = 0;
        public static final int REVIEW_RATING_STYLE_THUMBS_UP_DOWN = 1;
        public static final int REVIEW_RATING_STYLE_PERCENTAGE = 2;

        public static final String COLUMN_INPUT_ID = "input_id";
        public static final String COLUMN_CHANNEL_ID = "channel_id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_SEASON_DISPLAY_NUMBER = "season_display_number";
        public static final String COLUMN_EPISODE_DISPLAY_NUMBER = "episode_display_number";
        public static final String COLUMN_SEASON_TITLE = "season_title";
        public static final String COLUMN_EPISODE_TITLE = "episode_title";
        public static final String COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis";
        public static final String COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis";
        public static final String COLUMN_BROADCAST_GENRE = "broadcast_genre";
        public static final String COLUMN_CANONICAL_GENRE = "canonical_genre";
        public static final String COLUMN_SHORT_DESCRIPTION = "short_description";
        public static final String COLUMN_LONG_DESCRIPTION = "long_description";
        public static final String COLUMN_VIDEO_WIDTH = "video_width";
        public static final String COLUMN_VIDEO_HEIGHT = "video_height";
        public static final String COLUMN_AUDIO_LANGUAGE = "audio_language";
        public static final String COLUMN_CONTENT_RATING = "content_rating";
        public static final String COLUMN_POSTER_ART_URI = "poster_art_uri";
        public static final String COLUMN_THUMBNAIL_URI = "thumbnail_uri";
        public static final String COLUMN_SEARCHABLE = "searchable";
        public static final String COLUMN_RECORDING_DATA_URI = "recording_data_uri";
        public static final String COLUMN_RECORDING_DATA_BYTES = "recording_data_bytes";
        public static final String COLUMN_RECORDING_DURATION_MILLIS = "recording_duration_millis";
        public static final String COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS =
                "recording_expire_time_utc_millis";
        public static final String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id";
        public static final String COLUMN_INTERNAL_PROVIDER_DATA = "internal_provider_data";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG1 = "internal_provider_flag1";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG2 = "internal_provider_flag2";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG3 = "internal_provider_flag3";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG4 = "internal_provider_flag4";
        public static final String COLUMN_REVIEW_RATING_STYLE = "review_rating_style";
        public static final String COLUMN_REVIEW_RATING = "review_rating";
        public static final String COLUMN_VERSION_NUMBER = "version_number";
        public static final String COLUMN_SERIES_ID = "series_id";
        public static final String COLUMN_MULTI_SERIES_ID = "multi_series_id";
        public static final String COLUMN_SPLIT_ID = "split_id";

        private RecordedPrograms() {
        }
    }

    public static final class PreviewPrograms implements BaseTvColumns {
        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/" + PATH_PREVIEW_PROGRAM);

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/preview_program";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/preview_program";

        public static final int TYPE_MOVIE = 0;
        public static final int TYPE_TV_SERIES = 1;
        public static final int TYPE_TV_SEASON = 2;
        public static final int TYPE_TV_EPISODE = 3;
        public static final int TYPE_CLIP = 4;
        public static final int TYPE_EVENT = 5;
        public static final int TYPE_CHANNEL = 6;
        public static final int TYPE_TRACK = 7;
        public static final int TYPE_ALBUM = 8;
        public static final int TYPE_ARTIST = 9;
        public static final int TYPE_PLAYLIST = 10;
        public static final int TYPE_STATION = 11;

        public static final int ASPECT_RATIO_16_9 = 0;
        public static final int ASPECT_RATIO_3_2 = 1;
        public static final int ASPECT_RATIO_4_3 = 2;
        public static final int ASPECT_RATIO_1_1 = 3;
        public static final int ASPECT_RATIO_2_3 = 4;

        public static final int AVAILABILITY_AVAILABLE = 0;
        public static final int AVAILABILITY_FREE_WITH_SUBSCRIPTION = 1;
        public static final int AVAILABILITY_PAID_CONTENT = 2;

        public static final int INTERACTION_TYPE_VIEWS = 0;
        public static final int INTERACTION_TYPE_LISTENS = 1;
        public static final int INTERACTION_TYPE_FOLLOWERS = 2;
        public static final int INTERACTION_TYPE_FANS = 3;
        public static final int INTERACTION_TYPE_LIKES = 4;
        public static final int INTERACTION_TYPE_THUMBS = 5;
        public static final int INTERACTION_TYPE_VIEWERS = 6;

        public static final int REVIEW_RATING_STYLE_STARS = 0;
        public static final int REVIEW_RATING_STYLE_THUMBS_UP_DOWN = 1;
        public static final int REVIEW_RATING_STYLE_PERCENTAGE = 2;

        public static final String COLUMN_CHANNEL_ID = "channel_id";
        public static final String COLUMN_WEIGHT = "weight";
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_POSTER_ART_ASPECT_RATIO = "poster_art_aspect_ratio";
        public static final String COLUMN_THUMBNAIL_ASPECT_RATIO =
                "poster_thumbnail_aspect_ratio";
        public static final String COLUMN_LOGO_URI = "logo_uri";
        public static final String COLUMN_AVAILABILITY = "availability";
        public static final String COLUMN_STARTING_PRICE = "starting_price";
        public static final String COLUMN_OFFER_PRICE = "offer_price";
        public static final String COLUMN_RELEASE_DATE = "release_date";
        public static final String COLUMN_ITEM_COUNT = "item_count";
        public static final String COLUMN_LIVE = "live";
        public static final String COLUMN_INTENT_URI = "intent_uri";
        public static final String COLUMN_PREVIEW_VIDEO_URI = "preview_video_uri";
        public static final String COLUMN_LAST_PLAYBACK_POSITION_MILLIS =
                "last_playback_position_millis";
        public static final String COLUMN_DURATION_MILLIS = "duration_millis";
        public static final String COLUMN_INTERACTION_TYPE = "interaction_type";
        public static final String COLUMN_INTERACTION_COUNT = "interaction_count";
        public static final String COLUMN_AUTHOR = "author";
        public static final String COLUMN_BROWSABLE = "browsable";
        public static final String COLUMN_CONTENT_ID = "content_id";
        public static final String COLUMN_TRANSIENT = "transient";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_SEASON_DISPLAY_NUMBER = "season_display_number";
        public static final String COLUMN_EPISODE_DISPLAY_NUMBER = "episode_display_number";
        public static final String COLUMN_SEASON_TITLE = "season_title";
        public static final String COLUMN_EPISODE_TITLE = "episode_title";
        public static final String COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis";
        public static final String COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis";
        public static final String COLUMN_CANONICAL_GENRE = "canonical_genre";
        public static final String COLUMN_SHORT_DESCRIPTION = "short_description";
        public static final String COLUMN_LONG_DESCRIPTION = "long_description";
        public static final String COLUMN_VIDEO_WIDTH = "video_width";
        public static final String COLUMN_VIDEO_HEIGHT = "video_height";
        public static final String COLUMN_AUDIO_LANGUAGE = "audio_language";
        public static final String COLUMN_CONTENT_RATING = "content_rating";
        public static final String COLUMN_POSTER_ART_URI = "poster_art_uri";
        public static final String COLUMN_THUMBNAIL_URI = "thumbnail_uri";
        public static final String COLUMN_SEARCHABLE = "searchable";
        public static final String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id";
        public static final String COLUMN_INTERNAL_PROVIDER_DATA = "internal_provider_data";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG1 = "internal_provider_flag1";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG2 = "internal_provider_flag2";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG3 = "internal_provider_flag3";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG4 = "internal_provider_flag4";
        public static final String COLUMN_REVIEW_RATING_STYLE = "review_rating_style";
        public static final String COLUMN_REVIEW_RATING = "review_rating";
        public static final String COLUMN_VERSION_NUMBER = "version_number";
        public static final String COLUMN_SERIES_ID = "series_id";
        public static final String COLUMN_SPLIT_ID = "split_id";

        private PreviewPrograms() {
        }
    }

    public static final class WatchNextPrograms implements BaseTvColumns {
        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/" + PATH_WATCH_NEXT_PROGRAM);

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/watch_next_program";
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/watch_next_program";

        public static final int WATCH_NEXT_TYPE_CONTINUE = 0;
        public static final int WATCH_NEXT_TYPE_NEXT = 1;
        public static final int WATCH_NEXT_TYPE_NEW = 2;
        public static final int WATCH_NEXT_TYPE_WATCHLIST = 3;

        public static final int TYPE_MOVIE = 0;
        public static final int TYPE_TV_SERIES = 1;
        public static final int TYPE_TV_SEASON = 2;
        public static final int TYPE_TV_EPISODE = 3;
        public static final int TYPE_CLIP = 4;
        public static final int TYPE_EVENT = 5;
        public static final int TYPE_CHANNEL = 6;
        public static final int TYPE_TRACK = 7;
        public static final int TYPE_ALBUM = 8;
        public static final int TYPE_ARTIST = 9;
        public static final int TYPE_PLAYLIST = 10;
        public static final int TYPE_STATION = 11;

        public static final int ASPECT_RATIO_16_9 = 0;
        public static final int ASPECT_RATIO_3_2 = 1;
        public static final int ASPECT_RATIO_4_3 = 2;
        public static final int ASPECT_RATIO_1_1 = 3;
        public static final int ASPECT_RATIO_2_3 = 4;

        public static final int AVAILABILITY_AVAILABLE = 0;
        public static final int AVAILABILITY_FREE_WITH_SUBSCRIPTION = 1;
        public static final int AVAILABILITY_PAID_CONTENT = 2;

        public static final int INTERACTION_TYPE_VIEWS = 0;
        public static final int INTERACTION_TYPE_LISTENS = 1;
        public static final int INTERACTION_TYPE_FOLLOWERS = 2;
        public static final int INTERACTION_TYPE_FANS = 3;
        public static final int INTERACTION_TYPE_LIKES = 4;
        public static final int INTERACTION_TYPE_THUMBS = 5;
        public static final int INTERACTION_TYPE_VIEWERS = 6;

        public static final int REVIEW_RATING_STYLE_STARS = 0;
        public static final int REVIEW_RATING_STYLE_THUMBS_UP_DOWN = 1;
        public static final int REVIEW_RATING_STYLE_PERCENTAGE = 2;

        public static final String COLUMN_WATCH_NEXT_TYPE = "watch_next_type";
        public static final String COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS =
                "last_engagement_time_utc_millis";
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_POSTER_ART_ASPECT_RATIO = "poster_art_aspect_ratio";
        public static final String COLUMN_THUMBNAIL_ASPECT_RATIO =
                "poster_thumbnail_aspect_ratio";
        public static final String COLUMN_LOGO_URI = "logo_uri";
        public static final String COLUMN_AVAILABILITY = "availability";
        public static final String COLUMN_STARTING_PRICE = "starting_price";
        public static final String COLUMN_OFFER_PRICE = "offer_price";
        public static final String COLUMN_RELEASE_DATE = "release_date";
        public static final String COLUMN_ITEM_COUNT = "item_count";
        public static final String COLUMN_LIVE = "live";
        public static final String COLUMN_INTENT_URI = "intent_uri";
        public static final String COLUMN_PREVIEW_VIDEO_URI = "preview_video_uri";
        public static final String COLUMN_LAST_PLAYBACK_POSITION_MILLIS =
                "last_playback_position_millis";
        public static final String COLUMN_DURATION_MILLIS = "duration_millis";
        public static final String COLUMN_INTERACTION_TYPE = "interaction_type";
        public static final String COLUMN_INTERACTION_COUNT = "interaction_count";
        public static final String COLUMN_AUTHOR = "author";
        public static final String COLUMN_BROWSABLE = "browsable";
        public static final String COLUMN_CONTENT_ID = "content_id";
        public static final String COLUMN_TRANSIENT = "transient";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_SEASON_DISPLAY_NUMBER = "season_display_number";
        public static final String COLUMN_EPISODE_DISPLAY_NUMBER = "episode_display_number";
        public static final String COLUMN_SEASON_TITLE = "season_title";
        public static final String COLUMN_EPISODE_TITLE = "episode_title";
        public static final String COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis";
        public static final String COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis";
        public static final String COLUMN_CANONICAL_GENRE = "canonical_genre";
        public static final String COLUMN_SHORT_DESCRIPTION = "short_description";
        public static final String COLUMN_LONG_DESCRIPTION = "long_description";
        public static final String COLUMN_VIDEO_WIDTH = "video_width";
        public static final String COLUMN_VIDEO_HEIGHT = "video_height";
        public static final String COLUMN_AUDIO_LANGUAGE = "audio_language";
        public static final String COLUMN_CONTENT_RATING = "content_rating";
        public static final String COLUMN_POSTER_ART_URI = "poster_art_uri";
        public static final String COLUMN_THUMBNAIL_URI = "thumbnail_uri";
        public static final String COLUMN_SEARCHABLE = "searchable";
        public static final String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id";
        public static final String COLUMN_INTERNAL_PROVIDER_DATA = "internal_provider_data";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG1 = "internal_provider_flag1";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG2 = "internal_provider_flag2";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG3 = "internal_provider_flag3";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG4 = "internal_provider_flag4";
        public static final String COLUMN_REVIEW_RATING_STYLE = "review_rating_style";
        public static final String COLUMN_REVIEW_RATING = "review_rating";
        public static final String COLUMN_VERSION_NUMBER = "version_number";
        public static final String COLUMN_SERIES_ID = "series_id";
        public static final String COLUMN_SPLIT_ID = "split_id";

        private WatchNextPrograms() {
        }
    }

    public static String buildInputId(ComponentName name) {
        return name.flattenToShortString();
    }

    public static Uri buildChannelUri(long channelId) {
        return Uri.withAppendedPath(Channels.CONTENT_URI, String.valueOf(channelId));
    }

    public static Uri buildChannelUriForPassthroughInput(String inputId) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(PATH_PASSTHROUGH)
                .appendPath(inputId)
                .build();
    }

    public static Uri buildChannelLogoUri(long channelId) {
        return buildChannelLogoUri(buildChannelUri(channelId));
    }

    public static Uri buildChannelLogoUri(Uri channelUri) {
        if (!isChannelUriForTunerInput(channelUri)) {
            throw new IllegalArgumentException("Not a channel: " + channelUri);
        }
        return Uri.withAppendedPath(channelUri, Channels.Logo.CONTENT_DIRECTORY);
    }

    public static Uri buildChannelsUriForInput(String inputId) {
        Uri.Builder builder = Channels.CONTENT_URI.buildUpon();
        if (inputId != null) {
            builder.appendQueryParameter(PARAM_INPUT, inputId);
        }
        return builder.build();
    }

    public static Uri buildProgramUri(long programId) {
        return Uri.withAppendedPath(Programs.CONTENT_URI, String.valueOf(programId));
    }

    public static Uri buildProgramsUriForChannel(long channelId) {
        return Programs.CONTENT_URI.buildUpon()
                .appendQueryParameter(PARAM_CHANNEL, String.valueOf(channelId))
                .build();
    }

    public static Uri buildProgramsUriForChannel(Uri channelUri) {
        if (!isChannelUriForTunerInput(channelUri)) {
            throw new IllegalArgumentException("Not a channel: " + channelUri);
        }
        return buildProgramsUriForChannel(parseIdFromUri(channelUri));
    }

    public static Uri buildProgramsUriForChannel(
            long channelId, long startTime, long endTime) {
        return buildProgramsUriForChannel(channelId).buildUpon()
                .appendQueryParameter(PARAM_START_TIME, String.valueOf(startTime))
                .appendQueryParameter(PARAM_END_TIME, String.valueOf(endTime))
                .build();
    }

    public static Uri buildProgramsUriForChannel(
            Uri channelUri, long startTime, long endTime) {
        if (!isChannelUriForTunerInput(channelUri)) {
            throw new IllegalArgumentException("Not a channel: " + channelUri);
        }
        return buildProgramsUriForChannel(parseIdFromUri(channelUri), startTime, endTime);
    }

    public static Uri buildRecordedProgramUri(long recordedProgramId) {
        return Uri.withAppendedPath(
                RecordedPrograms.CONTENT_URI, String.valueOf(recordedProgramId));
    }

    public static Uri buildPreviewProgramUri(long previewProgramId) {
        return Uri.withAppendedPath(
                PreviewPrograms.CONTENT_URI, String.valueOf(previewProgramId));
    }

    public static Uri buildPreviewProgramsUriForChannel(long channelId) {
        return PreviewPrograms.CONTENT_URI.buildUpon()
                .appendQueryParameter(PARAM_CHANNEL, String.valueOf(channelId))
                .build();
    }

    public static Uri buildPreviewProgramsUriForChannel(Uri channelUri) {
        if (!isChannelUriForTunerInput(channelUri)) {
            throw new IllegalArgumentException("Not a channel: " + channelUri);
        }
        return buildPreviewProgramsUriForChannel(parseIdFromUri(channelUri));
    }

    public static Uri buildWatchNextProgramUri(long watchNextProgramId) {
        return Uri.withAppendedPath(
                WatchNextPrograms.CONTENT_URI, String.valueOf(watchNextProgramId));
    }

    private static long parseIdFromUri(Uri uri) {
        String last = uri.getLastPathSegment();
        return Long.parseLong(last);
    }

    private static boolean isTvUri(Uri uri) {
        return uri != null
                && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                && AUTHORITY.equals(uri.getAuthority());
    }

    private static boolean isTwoSegmentUriStartingWith(Uri uri, String pathSegment) {
        List<String> pathSegments = uri.getPathSegments();
        return pathSegments != null
                && pathSegments.size() == 2
                && pathSegment.equals(pathSegments.get(0));
    }

    public static boolean isChannelUri(Uri uri) {
        return isChannelUriForTunerInput(uri) || isChannelUriForPassthroughInput(uri);
    }

    public static boolean isChannelUriForTunerInput(Uri uri) {
        return isTvUri(uri) && isTwoSegmentUriStartingWith(uri, PATH_CHANNEL);
    }

    public static boolean isChannelUriForPassthroughInput(Uri uri) {
        return isTvUri(uri) && isTwoSegmentUriStartingWith(uri, PATH_PASSTHROUGH);
    }

    public static boolean isProgramUri(Uri uri) {
        return isTvUri(uri) && isTwoSegmentUriStartingWith(uri, PATH_PROGRAM);
    }

    public static boolean isRecordedProgramUri(Uri uri) {
        return isTvUri(uri) && isTwoSegmentUriStartingWith(uri, PATH_RECORDED_PROGRAM);
    }

    public static void requestChannelBrowsable(Context context, long channelId) {
        Objects.requireNonNull(context, "context cannot be null");
    }
}
