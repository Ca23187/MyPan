package com.mypan.common.constants;

import java.util.concurrent.TimeUnit;

public final class Constants {

    public static final int CAPTCHA_LENGTH = 5;
    public static final int EMAIL_CODE_LENGTH = 5;
    public static final String REDIS_KEY_CAPTCHA = "mypan:auth:captcha:";
    public static final String REDIS_KEY_CAPTCHA_FOR_EMAIL = "mypan:auth:captcha:email:";
    public static final String REDIS_KEY_EMAIL_CODE = "mypan:auth:email-code:";
    public static final long REDIS_TTL_CAPTCHA = 5L;
    public static final long REDIS_TTL_EMAIL_CODE = 15L;
    public static final TimeUnit REDIS_TIME_UNIT_CAPTCHA = TimeUnit.MINUTES;
    public static final int EMAIL_CODE_TYPE_REGISTER = 0;

    public static final String REDIS_KEY_LOGIN_USER = "mypan:auth:login-user:";

    public static final int QQ_LOGIN_STATE_LENGTH = 30;
    public static final String REDIS_KEY_QQ_LOGIN_STATE = "mypan:auth:qq:login-state:";
    public static final long REDIS_TTL_QQ_LOGIN = 5L;
    public static final TimeUnit REDIS_TIME_UNIT_QQ_LOGIN = TimeUnit.MINUTES;
    public static final String VIEW_OBJ_RESULT_KEY = "result";

    public static final String REDIS_KEY_USER_SPACE_INFO = "mypan:user:space-info:";
    public static final long REDIS_TTL_USER_SPACE_INFO = 1L;
    public static final TimeUnit REDIS_TIME_UNIT_USER_SPACE_INFO = TimeUnit.DAYS;

    public static final String REDIS_KEY_USER_PROFILE = "mypan:user:profile:";
    public static final long REDIS_TTL_USER_PROFILE = 6L;
    public static final TimeUnit REDIS_TIME_USER_PROFILE = TimeUnit.HOURS;

    public static final String REDIS_KEY_SYS_SETTING = "mypan:sys:setting:";

    public static final String REDIS_KEY_UPLOAD_TMP_TOTAL_FMT  = "mypan:upload:temp:{%s:%s}:total";
    public static final String REDIS_KEY_UPLOAD_TMP_CHUNKS_FMT = "mypan:upload:temp:{%s:%s}:chunks";
    public static final long REDIS_TTL_UPLOAD_TMP_SIZE = 1L;
    public static final TimeUnit REDIS_TIME_UNIT_UPLOAD_TMP_SIZE = TimeUnit.HOURS;

    public static final String FILE_FOLDER_FILE = "file";
    public static final String FILE_FOLDER_AVATAR_NAME = "avatar";
    public static final String FILE_FOLDER_TEMP = "temp";
    public static final String ROOT_PID = "0";

    public static final String AVATAR_SUFFIX = ".png";
    public static final String AVATAR_TYPE = "image/png";
    public static final String DEFAULT_AVATAR_NAME = "default_avatar.png";

    public static final String VIDEO_COVER_SUFFIX = ".png";
    public static final String VIDEO_COVER_TYPE = "image/png";
    public static final String AUDIO_COVER_SUFFIX = ".png";
    public static final String AUDIO_COVER_TYPE = "image/png";
    public static final int THUMBNAIL_WIDTH = 150;

    public static final String M3U8_NAME = "index.m3u8";

    public static final long MB = 1024 * 1024L;

    public static final int PAGE_SIZE = 15;

    public static final int RANDOM_FILE_ID_LENGTH = 10;

    public static final int BFS_BATCH_SIZE = 500;

    public static final String REDIS_KEY_DL_REQ_FMT      = "mypan:download:{%s}:req";
    public static final String REDIS_KEY_DL_INFLIGHT_FMT = "mypan:download:{%s}:inflight";
    public static final String REDIS_KEY_DL_ZIP_LOCK_FMT = "mypan:download:{%s}:zip:lock";
    public static final String REDIS_KEY_DL_RATE_FMT     = "mypan:download:{%s}:rate:%s";

    public static final int DOWNLOAD_CODE_LENGTH = 50;
    public static final int REDIS_TTL_DL_CODE = 1;
    public static final TimeUnit REDIS_TIME_UNIT_DL_CODE = TimeUnit.HOURS;

    public static final int SINGLE_DL_MAX_INFLIGHT = 8;
    public static final int REDIS_TTL_SINGLE_DL_INFLIGHT = 120;
    public static final TimeUnit REDIS_TIME_UNIT_SINGLE_DL_INFLIGHT = TimeUnit.SECONDS;

    public static final int DL_RATE_MAX_PER_MIN = 30;
    public static final int REDIS_TTL_DL_RATE = 60;
    public static final TimeUnit REDIS_TIME_UNIT_DL_RATE = TimeUnit.SECONDS;

    public static final long REDIS_TTL_ZIP_DL_LOCK = 30;
    public static final TimeUnit REDIS_TIME_UNIT_ZIP_DL_LOCK = TimeUnit.MINUTES;

    public static final String REDIS_KEY_OBJ_META = "mypan:download:meta:";
    public static final int REDIS_TTL_OBJ_META = 60;
    public static final TimeUnit REDIS_TIME_UNIT_OBJ_META = TimeUnit.SECONDS;

    public static final String REDIS_KEY_NEG = "mypan:neg:";
    public static final int REDIS_TTL_NEG = 30;
    public static final TimeUnit REDIS_TIME_UNIT_NEG = TimeUnit.SECONDS;

    public static final String REDIS_KEY_READ_PLAN = "mypan:read-plan:";
    public static final int REDIS_TTL_READ_PLAN_TS = 60;
    public static final int REDIS_TTL_READ_PLAN_NORM = 120;
    public static final TimeUnit REDIS_TIME_UNIT_READ_PLAN = TimeUnit.SECONDS;

    public static final String REDIS_KEY_VIDEO_BASE_FOLDER_FMT      = "mypan:read-plan:{%s:%s}:vbf";
    public static final String REDIS_KEY_VIDEO_BASE_FOLDER_LOCK_FMT = "mypan:read-plan:{%s:%s}:vbf:lock";
    public static final int REDIS_TTL_BASE_FOLDER = 300;
    public static final TimeUnit REDIS_TIME_UNIT_BASE_FOLDER = TimeUnit.SECONDS;

    public static final int SHARE_CODE_LENGTH = 5;
    public static final int SHARE_ID_LENGTH = 20;
    public static final int COOKIE_SHARE_ID_MAX_NUM = 10;

    public static final int SHARE_ACCESS_KEY_LENGTH = 32;
    public static final String COOKIE_SHARE_ACCESS = "share_access";

    public static final String REDIS_KEY_SHARE_ACCESS = "mypan:share:access:";
    public static final long REDIS_TTL_SHARE_ACCESS = 7;
    public static final TimeUnit REDIS_TIME_UNIT_SHARE_ACCESS = TimeUnit.DAYS;

    public static final String REDIS_KEY_UPLOAD_LOCAL_MERGE_LOCK_FMT = "mypan:upload:local:{%s:%s}:mergeLock";
    public static final long REDIS_TTL_UPLOAD_LOCAL_MERGE_LOCK = 10L;
    public static final TimeUnit REDIS_TIME_UNIT_UPLOAD_LOCAL_MERGE_LOCK = TimeUnit.MINUTES;


    public static final String REDIS_KEY_MPU_UPLOAD_ID_FMT     = "mypan:upload:mpu:{%s:%s}:uploadId";
    public static final String REDIS_KEY_MPU_ETAGS_FMT         = "mypan:upload:mpu:{%s:%s}:etags";
    public static final String REDIS_KEY_MPU_OBJECT_KEY_FMT    = "mypan:upload:mpu:{%s:%s}:objectKey";
    public static final String REDIS_KEY_MPU_COMPLETE_LOCK_FMT = "mypan:upload:mpu:{%s:%s}:completeLock";

    public static final long REDIS_TTL_MPU_COMPLETE_LOCK = 2L;
    public static final TimeUnit REDIS_TIME_UNIT_MPU_COMPLETE_LOCK = TimeUnit.MINUTES;

    public static final long REDIS_TTL_MPU = 6L;
    public static final TimeUnit REDIS_TIME_UNIT_MPU = TimeUnit.HOURS;

    public static final int TRANSCODE_STUCK_HOURS = 2;
    public static final int RECYCLE_EXPIRE_DAYS = 10;
    public static final int ORPHAN_TEMP_OLDER_THAN_HOURS = 24;
    public static final int LOCAL_TMP_OLDER_THAN_HOURS = 24;
    public static final int MINIO_STALE_MPU_TIME = 12;
    public static final int MINIO_MAX_SCANNED_MPU = 2000;
}
