package com.mypan.schedule;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileDelFlagEnum;
import com.mypan.common.enums.FileFolderTypeEnum;
import com.mypan.common.enums.FileStatusEnum;
import com.mypan.common.enums.FileTypeEnum;
import com.mypan.common.utils.string.StringTools;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.jpa.repository.FileInfoRepository;
import com.mypan.infra.jpa.repository.FileShareRepository;
import com.mypan.infra.jpa.repository.UserInfoRepository;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.infra.redis.RedisUtils;
import com.mypan.service.file.db.impl.FileInfoServiceImpl;
import com.mypan.service.file.storage.ObjectStorageService;
import com.mypan.service.schedule.FileCleanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupScheduler {

    private final FileInfoRepository fileInfoRepository;
    private final FileShareRepository fileShareRepository;
    private final UserInfoRepository userInfoRepository;
    private final FileCleanService fileCleanService;
    private final RedisUtils redisUtils;
    private final FileInfoServiceImpl fileInfoServiceImpl;
    private final ObjectProvider<ObjectStorageService> objectStorageProvider;
    private final RedisComponent redisComponent;

    private ObjectStorageService oss() {  // 可选注入，切 minio 就启用
        return objectStorageProvider.getIfAvailable();
    }

    private boolean isMinioEnabled() {
        return oss() != null;
    }

    /**
     * 定期删除 DEL 的记录 + 物理文件（含图片缩略图、视频封面、视频切片）
     * 秒传安全：只删“无任何非 DEL 引用”的 filePath
     */
    @Scheduled(cron = "0 0 */1 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void purgeDelRecordsAndPhysicalFiles() {

        List<FileInfo> dels = fileInfoRepository.findTop500ByDelFlagOrderByLastModifiedAtAsc(
                FileDelFlagEnum.DELETED.getFlag()
        );
        if (dels.isEmpty()) return;

        List<String> deleteIds = new ArrayList<>(dels.size());

        List<FileInfo> delFiles = dels.stream()  // 筛选出文件
                .filter(f -> Objects.equals(f.getFolderType(), FileFolderTypeEnum.FILE.getType()))
                .toList();

        // 1) 收集本批次所有“非空 filePath”（去重）
        List<String> paths = delFiles.stream()
                .map(FileInfo::getFilePath)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        // 2) 批量查哪些 path 仍被非 DEL 引用（秒传保护）
        Set<String> referenced = paths.isEmpty()
                ? Set.of()
                : new HashSet<>(fileInfoRepository.findReferencedPaths(paths, FileDelFlagEnum.DELETED.getFlag()));

        // 3) 可删除的物理 path
        Set<String> deletablePaths = paths.stream()
                .filter(p -> !referenced.contains(p))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 4) 给每个 filePath 选一条 FileInfo 作为“物理删除代表”（用于推导类型、封面/切片 key）
        Map<String, FileInfo> repByPath = delFiles.stream()
                .filter(f -> StringUtils.hasText(f.getFilePath()))
                .collect(Collectors.toMap(
                        FileInfo::getFilePath,
                        Function.identity(),
                        (a, b) -> a
                ));

        // 5) 物理删除：逐 path 删除一次；失败则从 deletablePaths 移除（保留记录下次重试）
        // NOTE: Iterator 可以确保遍历过程中删除元素之后照常遍历
        Iterator<String> it = deletablePaths.iterator();
        while (it.hasNext()) {
            String p = it.next();
            FileInfo rep = repByPath.get(p);
            if (rep == null) continue;

            try {
                deletePhysicalByFileInfo(rep);
            } catch (Exception e) {
                log.warn("purgeDel physical delete failed, filePath={}, fileId={}", p, rep.getFileId(), e);
                it.remove(); // 本轮物理没删成 → 不删对应记录
            }
        }

        /* 6) 删记录策略：
        - 目录：直接删
        - FILE 且 filePath 为空：直接删（坏数据不可恢复，不能卡住）
        - FILE 且 path 仍被引用：可以删记录（不删物理）
        - FILE 且 path 不被引用：仅当本轮物理删除成功（deletablePaths仍包含）才删记录
         */
        for (FileInfo f : dels) {
            if (!Objects.equals(f.getFolderType(), FileFolderTypeEnum.FILE.getType())) {
                deleteIds.add(f.getFileId());
                continue;
            }

            String fp = f.getFilePath();

            if (!StringUtils.hasText(fp)) {
                // 关键：异常数据直接删记录，避免永久残留
                deleteIds.add(f.getFileId());
                continue;
            }

            if (referenced.contains(fp)) {
                // 仍被引用：不删物理，但删这条 DEL 记录没问题
                deleteIds.add(f.getFileId());
                continue;
            }

            // 不被引用：本轮物理删成功才删记录
            if (deletablePaths.contains(fp)) {
                deleteIds.add(f.getFileId());
            }
        }

        if (!deleteIds.isEmpty()) {
            fileInfoRepository.deleteByFileIdIn(deleteIds);
        }
    }

    public void deletePhysicalByFileInfo(FileInfo f) {
        String filePath = f.getFilePath();
        if (!StringUtils.hasText(filePath)) return;

        // 0) 本地痕迹（在 local 模式=最终存储；在 minio 模式=缓存/产物）
        fileCleanService.cleanupLocalFileArtifacts(f);

        if (!isMinioEnabled()) {  // 只有对象存储（MinIO）才需要删存储对象（bucket 里的）
            return;  // local：最终文件已由 cleanupLocalFileArtifacts 删除完毕
        }
        ObjectStorageService os = oss();

        // 1) 删原文件对象（MinIO）
        os.delete(filePath);

        FileTypeEnum type = FileTypeEnum.getByType(f.getFileType());
        if (type == FileTypeEnum.IMAGE) {
            os.delete(filePath.replace(".", "_."));
            return;
        }

        if (type == FileTypeEnum.VIDEO) {
            String base = StringTools.removeSuffix(filePath);
            os.delete(base + Constants.VIDEO_COVER_SUFFIX);
            os.deleteByPrefix(base + "/");
        }

        if (type == FileTypeEnum.AUDIO) {
            String coverKey = StringTools.removeSuffix(filePath) + Constants.AUDIO_COVER_SUFFIX;
            os.delete(coverKey);
            os.delete(coverKey.replace(".", "_."));
        }
    }

    /**
     * 定期清理转码失败或卡住 TRANSCODING 的记录 + 文件 + 临时文件
     */
    @Scheduled(cron = "0 */30 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void purgeFailedOrStuckTranscodingFiles() {
        LocalDateTime stuckBefore = LocalDateTime.now().minusHours(Constants.TRANSCODE_STUCK_HOURS);

        List<FileInfo> candidates = fileInfoRepository.findTop500ByStatusInAndLastModifiedAtBefore(
                List.of(FileStatusEnum.TRANSCODING.getStatus(), FileStatusEnum.TRANSCODE_FAILED.getStatus()),
                stuckBefore
        );
        if (candidates.isEmpty()) return;

        for (FileInfo f : candidates) {
            String userId = f.getUserId();
            String fileId = f.getFileId();
            try {
                // temp 兜底（不管 minio/local 都做，避免切换遗留/异常残留）
                fileCleanService.cleanupUploadTemp(userId, fileId);
                if (isMinioEnabled()) {
                    redisComponent.clearMpu(userId, fileId);
                    redisComponent.clearTempUploadSize(userId, fileId);
                }
                f.setDelFlag(FileDelFlagEnum.DELETED.getFlag());
            } catch (Exception e) {
                log.warn("purgeTransfer cleanup failed, fileId={}, userId={}", fileId, userId, e);
            }
        }
        fileInfoRepository.saveAll(candidates);
    }

    /**
     * 定期删除过期分享记录
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void purgeExpiredShares() {
        int expiredRows = fileShareRepository.deleteByExpiredAtBefore(LocalDateTime.now());
        int foreverInvalidRows = fileShareRepository.deleteForeverSharesIfFileMissingOrDeleted(
                        FileDelFlagEnum.DELETED.getFlag());
        if (expiredRows > 0 || foreverInvalidRows > 0) {
            log.info(
                    "purgeExpiredShares expiredDeleted={}, foreverInvalidDeleted={}",
                    expiredRows, foreverInvalidRows
            );
        }
    }

    /**
     * 回收站超过 10 天：彻底删除（打 DEL + 回收空间）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void purgeRecycleOver10Days() {
        LocalDateTime before = LocalDateTime.now().minusDays(Constants.RECYCLE_EXPIRE_DAYS);

        List<FileInfo> roots = fileInfoRepository.findTop500ByDelFlagAndRecycledAtBeforeOrderByRecycledAtAsc(
                FileDelFlagEnum.RECYCLED.getFlag(),
                before
        );
        if (roots.isEmpty()) return;

        Map<String, List<String>> byUser = roots.stream()
                .collect(Collectors.groupingBy(
                        FileInfo::getUserId,
                        Collectors.mapping(FileInfo::getFileId, Collectors.toList())
                ));

        for (Map.Entry<String, List<String>> e : byUser.entrySet()) {
            fileInfoServiceImpl.delFiles(e.getKey(), e.getValue(), false);
        }
    }

    /**
     * 定期重算用户 usedSpace，并清理 redis 缓存
     */
    @Scheduled(cron = "0 30 4 * * ?") // 每天 04:30
    @Transactional(rollbackFor = Exception.class)
    public void recomputeUserSpaceAndClearRedis() {

        List<FileInfoRepository.UserUsedSpaceAgg> aggs =
                fileInfoRepository.sumUsedSpaceByUser(
                        FileFolderTypeEnum.FILE.getType(),
                        List.of(
                                FileDelFlagEnum.ACTIVE.getFlag(),
                                FileDelFlagEnum.RECYCLED.getFlag(),
                                FileDelFlagEnum.RECYCLED_CHILD.getFlag()
                ));

        for (FileInfoRepository.UserUsedSpaceAgg agg : aggs) {
            String userId = agg.getUserId();
            long usedSpace = agg.getUsed() == null ? 0L : agg.getUsed();

            userInfoRepository.updateUsedSpaceByUserId(usedSpace, userId);
            redisUtils.delete(Constants.REDIS_KEY_USER_SPACE_INFO + userId);
        }

        log.info("recomputeUserSpace updated {} users", aggs.size());
    }

    /**
     * 孤儿 temp 目录兜底清理（ Minio 用不到但建议兜底保留，Local 需要）
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void purgeOrphanTempFolders() {
        fileCleanService.purgeOrphanTempFolders(Constants.ORPHAN_TEMP_OLDER_THAN_HOURS);
    }

    /**
     * 清理找不到父节点的孤儿节点
     */
    @Scheduled(cron = "0 30 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void purgeOrphanRecords() {

        List<FileInfo> orphans = fileInfoRepository.findOrphanRecords(
                Constants.ROOT_PID,
                FileFolderTypeEnum.FOLDER.getType()
        );

        if (orphans.isEmpty()) return;

        Map<String, List<String>> byUser = orphans.stream()
                .collect(Collectors.groupingBy(
                        FileInfo::getUserId,
                        Collectors.mapping(FileInfo::getFileId, Collectors.toList())
                ));

        for (Map.Entry<String, List<String>> e : byUser.entrySet()) {
            fileInfoServiceImpl.delFiles(e.getKey(), e.getValue(), true);
        }

        log.warn("purgeOrphanRecords deleted {} orphan records", orphans.size());
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void purgeLocalTmpFiles() {
        fileCleanService.purgeLocalTmpFiles(Constants.LOCAL_TMP_OLDER_THAN_HOURS);
    }

    @Scheduled(cron = "0 10 */2 * * ?")
    public void purgeMinioStaleMultipartUploads() {
        if (!isMinioEnabled()) return;
        try {
            int aborted = oss().abortStaleMultipartUploads(
                    "",   // 扫 basePrefix 下全部（若 basePrefix 为空则扫全 bucket）
                    Constants.MINIO_STALE_MPU_TIME,   // 超过 12 小时还未完成的 MPU 才 abort，避免误伤慢上传
                    Constants.MINIO_MAX_SCANNED_MPU  // 单次最多扫描 2000 个 upload
            );
            if (aborted > 0) {
                log.info("purgeMinioStaleMultipartUploads aborted {}", aborted);
            }
        } catch (Exception e) {
            log.warn("purgeMinioStaleMultipartUploads failed", e);
        }
    }
}
