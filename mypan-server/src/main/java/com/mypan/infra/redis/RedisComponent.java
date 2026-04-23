package com.mypan.infra.redis;

import com.mypan.common.constants.Constants;
import com.mypan.infra.jpa.repository.UserInfoRepository;
import com.mypan.service.dto.SysSettingsDto;
import com.mypan.service.dto.UserSpaceDto;
import com.mypan.service.dto.download.DownloadRequestDto;
import com.mypan.service.dto.responseWrite.ObjMeta;
import com.mypan.service.dto.responseWrite.ReadPlan;
import com.mypan.service.dto.share.ShareAccessDto;
import com.mypan.web.dto.response.user.UserProfileVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisComponent {

    private final RedisUtils redisUtils;

    private final UserInfoRepository userInfoRepository;

    private final RedissonClient redissonClient;

    /**
     * 获取系统设置
     *
     */
    public SysSettingsDto getSysSettingsDto() {
        SysSettingsDto sysSettingsDto = redisUtils.get(Constants.REDIS_KEY_SYS_SETTING, SysSettingsDto.class);
        if (sysSettingsDto == null) {
            sysSettingsDto = new SysSettingsDto();
            redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, sysSettingsDto);
        }
        return sysSettingsDto;
    }

    public void saveSysSettingsDto(SysSettingsDto sysSettingsDto) {
        redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, sysSettingsDto);
    }

    public void saveCaptcha(String redisKey, String code) {
        redisUtils.setEx(
                redisKey,
                code,
                Constants.REDIS_TTL_CAPTCHA,
                Constants.REDIS_TIME_UNIT_CAPTCHA
        );
    }

    public void saveEmailCode(String redisKey, String code) {
        redisUtils.setEx(
                redisKey,
                code,
                Constants.REDIS_TTL_EMAIL_CODE,
                Constants.REDIS_TIME_UNIT_CAPTCHA
        );
    }

    public void saveLoginStatus(String userId, long ttlMillis) {
        redisUtils.setEx(
                Constants.REDIS_KEY_LOGIN_USER + userId,
                "1",
                ttlMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public void saveQqCallbackUrl(String state, String callbackUrl) {
        redisUtils.setEx(
                Constants.REDIS_KEY_QQ_LOGIN_STATE + state, 
                callbackUrl, 
                Constants.REDIS_TTL_QQ_LOGIN,
                Constants.REDIS_TIME_UNIT_QQ_LOGIN
        );
    }
    
    /**
     * 获取用户使用的空间
     *
     */
    public UserSpaceDto getUserSpaceInfo(String userId) {
        String key = Constants.REDIS_KEY_USER_SPACE_INFO + userId;
        UserSpaceDto userSpaceDto = redisUtils.get(key, UserSpaceDto.class);
        if (userSpaceDto == null) {
            userSpaceDto = userInfoRepository.findUserSpaceDtoByUserId(userId);
            redisUtils.setEx(key, userSpaceDto,
                    Constants.REDIS_TTL_USER_SPACE_INFO,
                    Constants.REDIS_TIME_UNIT_USER_SPACE_INFO);
        }
        return userSpaceDto;
    }

    public void saveUserProfileVo(String userId, UserProfileVo vo) {
        redisUtils.setEx(Constants.REDIS_KEY_USER_PROFILE + userId,
                vo,
                Constants.REDIS_TTL_USER_PROFILE,
                Constants.REDIS_TIME_USER_PROFILE);
    }

    public UserProfileVo getUserProfileVo(String userId) {
        return redisUtils.get(Constants.REDIS_KEY_USER_PROFILE + userId, UserProfileVo.class);
    }

    public void saveShareAccess(String shareId, String accessKey, ShareAccessDto dto) {
        long fixedTtlSeconds = Constants.REDIS_TIME_UNIT_SHARE_ACCESS
                .toSeconds(Constants.REDIS_TTL_SHARE_ACCESS);
        redisUtils.setEx(
                Constants.REDIS_KEY_SHARE_ACCESS + shareId + ":" + accessKey,
                dto,
                calcShareAccessTtlSeconds(dto.getExpiredAt(), fixedTtlSeconds),
                TimeUnit.SECONDS
        );
    }

    public ShareAccessDto getShareAccess(String shareId, String accessKey) {
        return redisUtils.get(Constants.REDIS_KEY_SHARE_ACCESS + shareId + ":" + accessKey,
                ShareAccessDto.class);
    }

    /**
     * 按规则计算 shareAccess 的 TTL（秒）：
     * - expiredAt != null: min(fixedTtlSeconds, expiredAt-now)
     * - expiredAt == null: fixedTtlSeconds
     * - 若已过期/不足 1 秒：返回 1（避免 setex 失败或立即过期的边界问题）
     */
    private long calcShareAccessTtlSeconds(Long expiredAt, long fixedTtlSeconds) {
        if (fixedTtlSeconds <= 0) return 60;  // 兜底：避免配置错误导致无 TTL
        if (expiredAt == null) return fixedTtlSeconds;
        long ttl = Math.min(fixedTtlSeconds,
                (expiredAt - System.currentTimeMillis()) / 1000);
        return Math.max(ttl, 1);  // 防止 0 或负数（Redis 不接受）
    }

    public ReadPlan getReadPlan(String userId, String fileId) {
        return redisUtils.get(Constants.REDIS_KEY_READ_PLAN + userId + ":" + fileId, ReadPlan.class);
    }

    public void saveReadPlan(String userId, String fileIdOrSegment, ReadPlan plan, int time) {
        redisUtils.setEx(
                Constants.REDIS_KEY_READ_PLAN + userId + ":" + fileIdOrSegment,
                plan,
                time,
                Constants.REDIS_TIME_UNIT_READ_PLAN
        );
    }

    public String getVideoBaseFolderKey(String userId, String fileId) {
        return String.format(Constants.REDIS_KEY_VIDEO_BASE_FOLDER_FMT, userId, fileId);
    }

    public String getVideoBaseFolder(String userId, String fileId) {
        return redisUtils.get(getVideoBaseFolderKey(userId, fileId));
    }

    public void saveVideoBaseFolder(String userId, String realFileId, String baseFolder) {
        redisUtils.setEx(
                getVideoBaseFolderKey(userId, realFileId),
                baseFolder,
                Constants.REDIS_TTL_BASE_FOLDER,
                Constants.REDIS_TIME_UNIT_BASE_FOLDER
        );
    }

    public RLock getVideoBaseFolderLock(String userId, String realFileId) {
        String lockKey = String.format(Constants.REDIS_KEY_VIDEO_BASE_FOLDER_LOCK_FMT, userId, realFileId);
        return redissonClient.getLock(lockKey);
    }

    public ObjMeta getObjMeta(String objectKey) {
        return redisUtils.get(Constants.REDIS_KEY_OBJ_META + objectKey, ObjMeta.class);
    }

    public void saveObjMeta(String objectKey, ObjMeta meta) {
        redisUtils.setEx(Constants.REDIS_KEY_OBJ_META + objectKey,
                meta,
                Constants.REDIS_TTL_OBJ_META,
                Constants.REDIS_TIME_UNIT_OBJ_META);
    }

    public boolean isNegCached(String... keys) {
        return redisUtils.exists(Constants.REDIS_KEY_NEG + String.join(":", keys));
    }

    public void saveNeg(String... keys) {
        redisUtils.setEx(Constants.REDIS_KEY_NEG + String.join(":", keys),
                "1",
                Constants.REDIS_TTL_NEG,
                Constants.REDIS_TIME_UNIT_NEG);
    }

    public String singleDownloadInflightKey(String code) {
        return String.format(Constants.REDIS_KEY_DL_INFLIGHT_FMT, code);
    }

//    public boolean tryAcquireSingleDownloadInflight(String code) {
//        String key = singleDownloadInflightKey(code);
//
//        // 1. 原子 +1（并确保有 TTL，防止泄漏）
//        Long current = redisUtils.incrByWithExpireOnFirstCreate(
//                key,
//                1,
//                Constants.REDIS_TTL_SINGLE_DL_INFLIGHT,
//                Constants.REDIS_TIME_UNIT_SINGLE_DL_INFLIGHT
//        );
//        if (current == null) {
//            return false;
//        }
//
//        // 2. 判断是否超过最大并发
//        if (current > Constants.SINGLE_DL_MAX_INFLIGHT) {
//            // 超限，立刻回滚
//            redisUtils.incrBy(key, -1);
//            return false;
//        }
//
//        // 3. 成功获得 inflight slot
//        return true;
//    }

//    public void releaseInflight(String code) {
//        String key = singleDownloadInflightKey(code);
//        Long v = redisUtils.incrBy(key, -1);
//        if (v == null) return;
//        if (v <= 0) redisUtils.delete(key);
//    }

    public String tryAcquireSingleDownloadPermit(String code) {
        String key = singleDownloadInflightKey(code);
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(key);

        try {
            // 每次都设置许可总数；重复调用也没关系，最终以同值为准
            semaphore.trySetPermits(Constants.SINGLE_DL_MAX_INFLIGHT);
            return semaphore.tryAcquire(1, Constants.REDIS_TTL_SINGLE_DL_INFLIGHT, Constants.REDIS_TIME_UNIT_SINGLE_DL_INFLIGHT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("tryAcquireSingleDownloadPermit failed, code={}", code, e);
            return null;
        }
    }

    public void releaseSingleDownloadPermit(String code, String permitId) {
        if (!StringUtils.hasText(permitId)) {
            return;
        }

        String key = singleDownloadInflightKey(code);
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(key);

        try {
            semaphore.release(permitId);
        } catch (Exception e) {
            log.warn("releaseSingleDownloadPermit failed, code={}, permitId={}", code, permitId, e);
        }
    }

    public String downloadReqKey(String code) {
        return String.format(Constants.REDIS_KEY_DL_REQ_FMT, code);
    }

    public void saveDownloadRequestDto(String code, DownloadRequestDto downloadRequestDto) {
        redisUtils.setEx(
                downloadReqKey(code),
                downloadRequestDto,
                Constants.REDIS_TTL_DL_CODE,
                Constants.REDIS_TIME_UNIT_DL_CODE
        );
    }

    public DownloadRequestDto getDownloadRequestDto(String code) {
        return redisUtils.get(downloadReqKey(code), DownloadRequestDto.class);
    }

    public RLock getZipDownloadLock(String code) {
        String lockKey = String.format(Constants.REDIS_KEY_DL_ZIP_LOCK_FMT, code);
        return redissonClient.getLock(lockKey);
    }

    public String downloadRateLimiterKey(String code, String ip) {
        return String.format(Constants.REDIS_KEY_DL_RATE_FMT, code, ip);
    }

//    public boolean tryAcquireDownloadRate(String code, String ip) {
//        String key = downloadRateLimiterKey(code, ip);
//
//        // 原子 +1，并设置 TTL（首次）
//        Long cnt = redisUtils.incrByWithExpireOnFirstCreate(
//                key,
//                1,
//                Constants.REDIS_TTL_DL_RATE,
//                Constants.REDIS_TIME_UNIT_DL_RATE
//        );
//
//        if (cnt == null) return false;
//
//        long windowSeconds = Constants.REDIS_TIME_UNIT_DL_RATE.toSeconds(Constants.REDIS_TTL_DL_RATE);
//        long limit = (windowSeconds / 60) * Constants.DL_RATE_MAX_PER_MIN;
//        if (windowSeconds % 60 != 0) limit += Constants.DL_RATE_MAX_PER_MIN; // 不整分钟按向上取整，或按你想要的策略
//        return cnt <= limit;
//    }

    public boolean tryAcquireDownloadRate(String code, String ip) {
        String key = downloadRateLimiterKey(code, ip);
        RRateLimiter limiter = redissonClient.getRateLimiter(key);

        try {
            long windowSeconds = Constants.REDIS_TIME_UNIT_DL_RATE
                    .toSeconds(Constants.REDIS_TTL_DL_RATE);

            long limit = (windowSeconds / 60) * Constants.DL_RATE_MAX_PER_MIN;
            if (windowSeconds % 60 != 0) {
                limit += Constants.DL_RATE_MAX_PER_MIN;
            }

            limiter.trySetRate(
                    RateType.OVERALL,
                    limit,
                    Duration.ofSeconds(windowSeconds)
            );

            return limiter.tryAcquire(1);
        } catch (Exception e) {
            log.error("tryAcquireDownloadRate failed, code={}, ip={}", code, ip, e);
            return false;
        }
    }

    /**
     * 幂等记录某个 chunk 的大小，并返回当前总大小（原子操作）
     * - 同 chunkIndex 重传：不会重复加
     * - chunk 大小变化：按差额修正
     */
    public Long recordUploadChunkSize(String userId, String fileId, int chunkIndex, long chunkSizeBytes) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(fileId)) return null;
        if (chunkIndex < 0 || chunkSizeBytes < 0) return null;

        String totalKey = tmpTotalKey(userId, fileId);
        String chunksKey = tmpChunksKey(userId, fileId);

        long ttlSeconds = Constants.REDIS_TIME_UNIT_UPLOAD_TMP_SIZE
                .toSeconds(Constants.REDIS_TTL_UPLOAD_TMP_SIZE);

        String scriptText = """
            local old = redis.call('hget', KEYS[2], ARGV[1])
            local newv = tonumber(ARGV[2]) or 0
            newv = math.floor(newv)
    
            if (not old) then
                redis.call('hset', KEYS[2], ARGV[1], tostring(newv))
                redis.call('incrby', KEYS[1], newv)
            else
                local o = tonumber(old) or 0
                o = math.floor(o)
                if (o ~= newv) then
                    redis.call('hset', KEYS[2], ARGV[1], tostring(newv))
                    local delta = newv - o
                    delta = math.floor(delta)
                    redis.call('incrby', KEYS[1], delta)
                end
            end
    
            local ttl = tonumber(ARGV[3]) or 0
            ttl = math.floor(ttl)
            if (ttl > 0) then
                redis.call('expire', KEYS[1], ttl)
                redis.call('expire', KEYS[2], ttl)
            end
    
            local total = redis.call('get', KEYS[1])
            if (not total) then
                return 0
            end
            return tonumber(total) or 0
        """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(scriptText);

        return redisUtils.execLongWithStrArgs(
                script,
                Arrays.asList(totalKey, chunksKey),
                String.valueOf(chunkIndex),
                String.valueOf(chunkSizeBytes),
                String.valueOf(ttlSeconds)
        );
    }

    public Long getUploadTotalSize(String userId, String fileId) {
        String v = redisUtils.get(tmpTotalKey(userId, fileId));
        if (!StringUtils.hasText(v)) return 0L;
        try { return Long.parseLong(v); } catch (Exception e) { return 0L; }
    }

    private String tmpTotalKey(String userId, String fileId) {
        return String.format(Constants.REDIS_KEY_UPLOAD_TMP_TOTAL_FMT, userId, fileId);
    }

    private String tmpChunksKey(String userId, String fileId) {
        return String.format(Constants.REDIS_KEY_UPLOAD_TMP_CHUNKS_FMT, userId, fileId);
    }

    /**
     * 清理上传临时统计（总大小 + 分片hash）
     */
    public void clearTempUploadSize(String userId, String fileId) {
        redisUtils.delete(tmpTotalKey(userId, fileId), tmpChunksKey(userId, fileId));
    }

    public String mpuUploadIdKey(String userId, String fileId) {
        return String.format(Constants.REDIS_KEY_MPU_UPLOAD_ID_FMT, userId, fileId);
    }
    public String mpuEtagsKey(String userId, String fileId) {
        return String.format(Constants.REDIS_KEY_MPU_ETAGS_FMT, userId, fileId);
    }
    public String mpuObjectKeyKey(String userId, String fileId) {
        return String.format(Constants.REDIS_KEY_MPU_OBJECT_KEY_FMT, userId, fileId);
    }

    public String mpuCompleteLockKey(String userId, String fileId) {
        return String.format(Constants.REDIS_KEY_MPU_COMPLETE_LOCK_FMT, userId, fileId);
    }

    public void clearMpu(String userId, String fileId) {
        redisUtils.delete(
                mpuUploadIdKey(userId, fileId),
                mpuEtagsKey(userId, fileId),
                mpuObjectKeyKey(userId, fileId)
        );
    }

    public RLock getMpuCompleteLock(String userId, String fileId) {
        return redissonClient.getLock(mpuCompleteLockKey(userId, fileId));
    }

    public String localMergeLockKey(String userId, String fileId) {
        return String.format(Constants.REDIS_KEY_UPLOAD_LOCAL_MERGE_LOCK_FMT, userId, fileId);
    }

    /**
     * 是否所有 chunk 都已记录（使用 upload temp chunks hash 判断，不扫磁盘）
     * - 先 fast path: hlen == chunks
     * - 再严格校验 0..chunks-1 每个 field 都存在（防止中间缺号但hlen刚好等于chunks的极端情况）
     */
    public boolean isAllLocalChunksUploaded(String userId, String fileId, int chunks) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(fileId) || chunks <= 0) return false;

        String chunksKey = tmpChunksKey(userId, fileId);

        Map<String, String> m = redisUtils.hgetAll(chunksKey);
        if (m == null || m.size() < chunks) return false;

        // 严格校验：0..chunks-1 全覆盖
        for (int i = 0; i < chunks; i++) {
            if (!m.containsKey(String.valueOf(i))) return false;
        }
        return true;
    }

    public RLock getLocalMergeLock(String userId, String fileId) {
        return redissonClient.getLock(localMergeLockKey(userId, fileId));
    }
}
