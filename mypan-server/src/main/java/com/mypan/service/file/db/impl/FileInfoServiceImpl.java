package com.mypan.service.file.db.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileDelFlagEnum;
import com.mypan.common.enums.FileFolderTypeEnum;
import com.mypan.common.enums.FileStatusEnum;
import com.mypan.common.enums.SearchScopeEnum;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.string.StringTools;
import com.mypan.config.AppProperties;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.jpa.entity.QFileInfo;
import com.mypan.infra.jpa.entity.QUserInfo;
import com.mypan.infra.jpa.querydsl.file.FileInfoQueryDsl;
import com.mypan.infra.jpa.querydsl.support.QueryDslUtils;
import com.mypan.infra.jpa.repository.FileInfoRepository;
import com.mypan.infra.mapstruct.FileInfoMapper;
import com.mypan.infra.sse.SseHub;
import com.mypan.infra.sse.TranscodeSseEvent;
import com.mypan.service.dto.share.ShareAccessDto;
import com.mypan.service.file.db.FileInfoService;
import com.mypan.service.file.db.support.FileBatchOperationManager;
import com.mypan.service.file.tree.FileTreeNavigator;
import com.mypan.service.user.UserInfoService;
import com.mypan.web.dto.query.FileInfoQuery;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.file.FileInfoVo;
import com.mypan.web.dto.response.file.FolderVo;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class FileInfoServiceImpl implements FileInfoService {
    private final QueryDslUtils queryDSLUtils;
    private final FileInfoRepository fileInfoRepository;
    private final AppProperties appProperties;
    private final FileInfoMapper fileInfoMapper;
    private final SseHub sseHub;
    private final FileBatchOperationManager manager;
    private final UserInfoService userInfoService;
    private final FileTreeNavigator tree;

    @Override
    public PaginationResultVo<FileInfoVo> pageMyFiles(String userId, FileInfoQuery query) {
        query.setUserId(userId);
        query.setDelFlag(FileDelFlagEnum.ACTIVE.getFlag());
        SearchScopeEnum scope = SearchScopeEnum.ofOrDefault(query.getSearchScope(), SearchScopeEnum.SCOPE_CURRENT_ONLY);
        query.setSearchScope(scope.getType());
        // 当前文件夹向下查询
        if (scope == SearchScopeEnum.SCOPE_CURRENT_RECURSIVE) {
            List<String> folderIds = tree.collectFolderIdsBfs(userId, query.getFilePid(), FileDelFlagEnum.ACTIVE.getFlag(), true);
            query.setFilePidIn(folderIds);
            query.setFilePid(null);
        }
        BooleanBuilder builder = FileInfoQueryDsl.buildPredicate(query);
        List<OrderSpecifier<?>> orders = FileInfoQueryDsl.buildOrderSpecifiers(query);
        QFileInfo qFileInfo = QFileInfo.fileInfo;
        return queryDSLUtils.findPageByParam(
                qFileInfo,
                builder,
                query.getPageNo(),
                query.getPageSize(),
                orders,
                FileInfoVo.selectBase(qFileInfo)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finalizeTranscoding(String userId, String fileId, long size, String cover, int newStatus) {
        int rows = fileInfoRepository.updateFileStatusWithOldStatus(
                fileId, userId,
                FileStatusEnum.TRANSCODING.getStatus(),
                size, cover, newStatus
        );
        if (rows > 0)
            sseHub.pushToUser(userId, new TranscodeSseEvent(
                    "TRANSCODE_STATUS", fileId, newStatus, cover, size));
    }

    @Override
    public FileInfo findByFileIdAndUserId(String fileId, String userId) {
        return fileInfoRepository.findByFileIdAndUserId(fileId, userId);
    }

    @Override
    public FileInfo findByFileIdAndUserIdAndDelFlag(String fileId, String userId, Integer delFlag) {
        return fileInfoRepository.findByFileIdAndUserIdAndDelFlag(fileId, userId, delFlag);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo createFolder(String filePid, String userId, String folderName) {
        checkFileNameOrFolderName(filePid, userId, folderName, FileFolderTypeEnum.FOLDER.getType());
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(StringTools.getRandomString(Constants.RANDOM_FILE_ID_LENGTH));
        fileInfo.setUserId(userId);
        fileInfo.setFilePid(filePid);
        fileInfo.setFileName(folderName);
        fileInfo.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        fileInfo.setStatus(FileStatusEnum.ACTIVE.getStatus());
        fileInfo.setDelFlag(FileDelFlagEnum.ACTIVE.getFlag());
        fileInfoRepository.save(fileInfo);
        return fileInfo;
    }

    // 检查文件或文件夹能否在当前目录下重命名，如果当前目录下已有同名文件或文件夹则不能重命名
    private void checkFileNameOrFolderName(String filePid, String userId, String fileName, Integer folderType) {
        FileInfo fileInfo = fileInfoRepository.findFirstByUserId_AndFolderType_AndFileName_AndFilePid_AndDelFlag(
                userId, folderType, fileName, filePid, FileDelFlagEnum.ACTIVE.getFlag());
        if (fileInfo != null)
            throw new BusinessException("A file/folder with the same name already exists in this directory. Please modify the name.");
    }

    @Override
    public List<FolderVo> getFolderInfoVoList(String path, String userId) {
        List<String> ids = StringTools.parseDelimitedDistinctList(path, "/");
        if (ids.isEmpty()) return List.of();
        List<FolderVo> voList = fileInfoRepository.findFolderInfoVoList(userId, FileFolderTypeEnum.FOLDER.getType(), FileDelFlagEnum.ACTIVE.getFlag(), ids);
        if (voList.size() != ids.size())
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        // 根据 path 顺序做一个顺序表
        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < ids.size(); i++)
            orderMap.put(ids.get(i), i);
        // 在内存中按顺序排序
        voList.sort(Comparator.comparingInt(
                vo -> orderMap.getOrDefault(vo.getFileId(), Integer.MAX_VALUE)
        ));
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoVo rename(String fileId, String userId, String newFileName) {
        StringTools.FileNameValidator.validateSimpleName(newFileName, true);
        FileInfoVo fileInfoVo = fileInfoRepository.findVoByFileIdAndUserIdAndDelFlag(fileId, userId, FileDelFlagEnum.ACTIVE.getFlag());
        if (null == fileInfoVo)
            throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
        if (fileInfoVo.getFileName().equals(newFileName))  // 新 name 与旧 name 一样，直接返回
            return fileInfoVo;
        String filePid = fileInfoVo.getFilePid();
        checkFileNameOrFolderName(filePid, userId, newFileName, fileInfoVo.getFolderType());
        // 如果是文件，则获取后缀
        if (FileFolderTypeEnum.FILE.getType().equals(fileInfoVo.getFolderType()))
            newFileName += StringTools.getSuffix(fileInfoVo.getFileName());
        LocalDateTime now = LocalDateTime.now();
        int count = fileInfoRepository.renameWithOldName(
                newFileName, now, fileId, userId,
                fileInfoVo.getFileName(),
                FileDelFlagEnum.ACTIVE.getFlag()
        );
        if (count == 0)
            throw new BusinessException("Renaming failed. Please refresh and try again.");
        fileInfoVo.setFileName(newFileName);
        fileInfoVo.setLastModifiedAt(now);
        return fileInfoVo;
    }

    @Override
    public List<FileInfoVo> findMovableTargetFolders(String userId, String filePid, String currentFileIds) {
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setDelFlag(FileDelFlagEnum.ACTIVE.getFlag());
        query.setFilePid(filePid);
        query.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        if (StringUtils.hasText(currentFileIds)) {
            List<String> excludeIds = StringTools.parseDelimitedDistinctList(currentFileIds, ",");
            query.setExcludeFileIdIn(excludeIds);
        }
        BooleanBuilder where = FileInfoQueryDsl.buildPredicate(query);
        QFileInfo f = QFileInfo.fileInfo;
        return queryDSLUtils.findListByParam(
                f,
                where,
                null, null,               // 不分页（一级一级点进去）
                List.of(f.createdAt.desc()),
                FileInfoVo.selectBase(f)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeFileLocation(String fileIds, String targetPid, String userId) {
        List<String> moveIds = StringTools.parseDelimitedDistinctList(fileIds, ".");
        if (moveIds.isEmpty()) return;

        // 1) 目标目录校验（根目录放行；非根目录必须存在且是目录且 USING）
        if (!Constants.ROOT_PID.equals(targetPid)) {
            FileInfo targetFolder = fileInfoRepository.findByFileIdAndUserIdAndDelFlag(
                    targetPid, userId, FileDelFlagEnum.ACTIVE.getFlag()
            );
            if (targetFolder == null || !FileFolderTypeEnum.FOLDER.getType().equals(targetFolder.getFolderType()))
                throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }

        // 2) 查出要移动的节点（只允许移动 USING）
        List<FileInfo> selected = fileInfoRepository.findByUserIdAndDelFlagAndFileIdIn(
                userId, FileDelFlagEnum.ACTIVE.getFlag(), moveIds
        );
        if (selected.isEmpty()) return;

        // 3) 自己不能移动到自己（A -> A）直接拦
        if (moveIds.contains(targetPid))
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

        // 4) 构建“本次请求内”的 parent 查询缓存
        //    目的：分水岭前校验 + 分水岭处父子过滤 都需要查父链，避免重复 hit DB。
        Map<String, FileInfo> parentCache = new HashMap<>();

        // 5) 不能把“目录”移动到它自己的子孙目录
        //    做法：从 targetPid 往上一路找祖先 pid，若命中任何被移动的目录 id，则非法
        Set<String> movingFolderIds = selected.stream()
                .filter(f -> FileFolderTypeEnum.FOLDER.getType().equals(f.getFolderType()))
                .map(FileInfo::getFileId)
                .collect(Collectors.toSet());

        if (!movingFolderIds.isEmpty() && !Constants.ROOT_PID.equals(targetPid)) {
            String cur = targetPid;
            while (cur != null && !Constants.ROOT_PID.equals(cur)) {
                if (movingFolderIds.contains(cur))  // 说明 targetPid 在某个被移动目录的子树里
                    throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
                // 取父节点：注意这里只能查 USING 目录，否则“已删除目录”不应当作为有效祖先
                FileInfo parent = parentCache.containsKey(cur)
                        ? parentCache.get(cur)
                        : fileInfoRepository.findByFileIdAndUserIdAndDelFlag(cur, userId, FileDelFlagEnum.ACTIVE.getFlag());

                // 写入缓存（允许缓存 null，避免重复查不存在的节点）
                parentCache.putIfAbsent(cur, parent);

                if (parent == null)  // 目标路径不完整（理论上前端不该传这种 pid），直接当非法
                    throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
                cur = parent.getFilePid();
            }
        }

        // ======= 分水岭：同级/跨级分流 =======
        boolean sameLevel = selected.stream()
                .map(FileInfo::getFilePid)
                .distinct()
                .count() == 1;

        if (!sameLevel && !movingFolderIds.isEmpty()) {
            // 跨级才做父子过滤：只保留最顶层选中项
            selected = manager.filterTopLevelSelections(
                    selected, userId, movingFolderIds, parentCache
            );
            if (selected.isEmpty()) return;
        }

        // 6) 目标目录已有名称占用集合（只看 USING）
        Set<String> occupied = fileInfoRepository.findFileNameByUserIdAndFilePidAndDelFlag(
                userId, targetPid, FileDelFlagEnum.ACTIVE.getFlag()
        );

        // 7) 重命名 + 移动（同批次内也要防冲突）
        for (FileInfo item : selected) {
            // 可选优化：同目录移动直接跳过（避免“移动到原目录”触发自己撞自己导致误改名）
            if (targetPid.equals(item.getFilePid()))
                continue;
            String original = item.getFileName();
            String candidate = StringTools.resolveConflict(original, occupied);
            if (!candidate.equals(original))
                item.setFileName(candidate);
            item.setFilePid(targetPid);
        }

        // 8) 显式保存
        fileInfoRepository.saveAll(selected);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveFiles2RecycleBin(String userId, String fileIds) {
        List<String> rootIdList = StringTools.parseDelimitedDistinctList(fileIds, ",");
        if (rootIdList.isEmpty()) return;
        List<FileInfo> roots = fileInfoRepository.findByUserIdAndDelFlagAndFileIdIn(
                userId, FileDelFlagEnum.ACTIVE.getFlag(), rootIdList);
        if (roots.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        // 更新根节点
        for (FileInfo root : roots) {
            root.setDelFlag(FileDelFlagEnum.RECYCLED.getFlag());
            root.setRecycledAt(now);
        }

        // BFS 收集所有后代
        List<FileInfo> children = tree.collectDescendantsBfs(
                userId,
                rootIdList,
                List.of(FileDelFlagEnum.ACTIVE.getFlag())
        );
        // 更新孩子节点
        for (FileInfo child : children) {
            child.setDelFlag(FileDelFlagEnum.RECYCLED_CHILD.getFlag());
            child.setRecycledAt(now);
        }

        // 一次性保存
        List<FileInfo> toSave = new ArrayList<>(children.size() + roots.size());
        toSave.addAll(roots);
        toSave.addAll(children);
        fileInfoRepository.saveAll(toSave);
    }

    @Override
    public PaginationResultVo<FileInfoVo> pageMyRecycledFiles(String userId,
                                                              Integer pageNo,
                                                              Integer pageSize) {
        FileInfoQuery query = new FileInfoQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setUserId(userId);
        query.setDelFlag(FileDelFlagEnum.RECYCLED.getFlag());  // 只显示顶层回收项
        BooleanBuilder builder = FileInfoQueryDsl.buildPredicate(query);
        QFileInfo qFileInfo = QFileInfo.fileInfo;
        return queryDSLUtils.findPageByParam(
                qFileInfo,
                builder,
                pageNo,
                pageSize,
                List.of(qFileInfo.recycledAt.desc()),
                FileInfoVo.selectBase(qFileInfo)
        );
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverFiles(String userId, String fileIds) {
        List<String> rootIdList = StringTools.parseDelimitedDistinctList(fileIds, ",");
        if (rootIdList.isEmpty()) return;
        List<FileInfo> roots = fileInfoRepository.findByUserIdAndDelFlagAndFileIdIn(
                userId, FileDelFlagEnum.RECYCLED.getFlag(), rootIdList
        );
        if (roots.isEmpty()) return;

        // 收集整棵子树（包含 root + 后代）
        List<FileInfo> descendants = tree.collectDescendantsBfs(
                userId,
                rootIdList,
                List.of(FileDelFlagEnum.RECYCLED_CHILD.getFlag())
        );

        List<FileInfo> allNodes = new ArrayList<>(roots.size() + descendants.size());
        allNodes.addAll(roots);
        allNodes.addAll(descendants);

        // 只给 root 算 targetPid + 重名处理
        Set<String> rootParentIds = roots.stream()
                .map(FileInfo::getFilePid)
                .filter(pid -> pid != null && !Constants.ROOT_PID.equals(pid))
                .collect(Collectors.toSet());

        Set<String> existingParentIds = rootParentIds.isEmpty()
                ? Set.of()
                : fileInfoRepository
                .findByUserIdAndDelFlagAndFolderTypeAndFileIdIn(
                        userId,
                        FileDelFlagEnum.ACTIVE.getFlag(),
                        FileFolderTypeEnum.FOLDER.getType(),
                        rootParentIds
                )
                .stream()
                .map(FileInfo::getFileId)
                .collect(Collectors.toSet());

        Map<String, Set<String>> occupiedByPid = new HashMap<>();

        for (FileInfo r : roots) {
            String pid = r.getFilePid();
            String targetPid =
                    (pid == null || Constants.ROOT_PID.equals(pid)) ? Constants.ROOT_PID
                            : existingParentIds.contains(pid) ? pid
                            : Constants.ROOT_PID;

            Set<String> occupied = occupiedByPid.computeIfAbsent(
                    targetPid,
                    k -> new HashSet<>(
                            fileInfoRepository.findFileNameByUserIdAndFilePidAndDelFlag(
                                    userId,
                                    targetPid,
                                    FileDelFlagEnum.ACTIVE.getFlag()
                            )
                    )
            );
            String newName = StringTools.resolveConflict(r.getFileName(), occupied);
            r.setFilePid(targetPid);
            r.setFileName(newName);
        }

        // 全量恢复
        for (FileInfo node : allNodes) {
            node.setDelFlag(FileDelFlagEnum.ACTIVE.getFlag());
            node.setRecycledAt(null);
        }
        fileInfoRepository.saveAll(allNodes);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delFilesUser(String userId, String fileIds) {
        List<String> ids = StringTools.parseDelimitedDistinctList(fileIds, ",");
        delFiles(userId, ids, false);
    }

    public void delFiles(String userId, List<String> rootIdList, boolean adminOp) {
        if (rootIdList == null || rootIdList.isEmpty()) return;

        List<Integer> adminFlags = List.of(
                FileDelFlagEnum.ACTIVE.getFlag(),
                FileDelFlagEnum.RECYCLED.getFlag(),
                FileDelFlagEnum.RECYCLED_CHILD.getFlag()
        );

        List<Integer> rootDelFlags = adminOp ? adminFlags
                : List.of(FileDelFlagEnum.RECYCLED.getFlag());

        List<Integer> childDelFlags = adminOp ? adminFlags
                : List.of(FileDelFlagEnum.RECYCLED_CHILD.getFlag());

        List<FileInfo> roots = fileInfoRepository.findByUserIdAndDelFlagInAndFileIdIn(
                userId, rootDelFlags, rootIdList
        );
        if (roots.isEmpty()) return;

        // 收集整棵子树（包含 root + 后代）
        List<FileInfo> descendants = tree.collectDescendantsBfs(
                userId,
                rootIdList,
                childDelFlags
        );

        List<FileInfo> allNodes = new ArrayList<>(roots.size() + descendants.size());
        allNodes.addAll(roots);
        allNodes.addAll(descendants);

        // 计算回收空间：只统计“文件”的 size
        long reclaimSize = allNodes.stream()
                .filter(n -> Objects.equals(n.getFolderType(), FileFolderTypeEnum.FILE.getType()))
                .map(FileInfo::getFileSize)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        // 批量置 DEL（oldFlag in）
        allNodes.forEach(n -> n.setDelFlag(FileDelFlagEnum.DELETED.getFlag()));

        // 扣用户空间 + 删缓存
        userInfoService.addUsedSpace(userId, -reclaimSize);
        fileInfoRepository.saveAll(allNodes);
    }

    @Override
    public List<FolderVo> getFolderBreadcrumb(String userId, String fileId) {
        Map<String, FileInfo> cache = new HashMap<>();
        List<FileInfo> chain = tree.buildActiveAncestorChain(userId, fileId, true, cache);

        // 只保留目录，并倒序成 root -> current
        List<FolderVo> out = chain.stream()
                .filter(f -> Objects.equals(f.getFolderType(), FileFolderTypeEnum.FOLDER.getType()))
                .map(fileInfoMapper::toFolderVo)
                .collect(Collectors.toList());
        Collections.reverse(out);
        return out;
    }


    @Override
    public PaginationResultVo<FileInfoVo> pageUserFiles(FileInfoQuery query) {
        QFileInfo f = QFileInfo.fileInfo;
        QUserInfo u = QUserInfo.userInfo;
        query.setExcludeDelFlag(FileDelFlagEnum.DELETED.getFlag());
        query.setExcludeUserIdIn(appProperties.getAdminIds());
        BooleanBuilder where = FileInfoQueryDsl.buildPredicate(query);
        if (StringUtils.hasText(query.getNicknameFuzzy()))
            where.and(u.nickname.containsIgnoreCase(query.getNicknameFuzzy().trim()));
        // join：只做 join/on（count + list 共用）
        return queryDSLUtils.findPageByParamWithJoin(
                f,
                where,
                query.getPageNo(),
                query.getPageSize(),
                List.of(f.lastModifiedAt.desc()),
                FileInfoVo.selectBaseWithUser(f, u),  // projection：File + 用户昵称
                f.fileId, // countDistinctKey，防止 join 放大
                q -> q.leftJoin(u).on(f.userId.eq(u.userId))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delFilesAdmin(String fileIdAndUserIds) {
        Map<String, List<String>> group = parseUserIdFileIds(fileIdAndUserIds);
        for (Map.Entry<String, List<String>> e : group.entrySet()) {
            String userId = e.getKey();
            List<String> fileIds = e.getValue();
            delFiles(userId, fileIds, true);
        }
    }

    /**
     * 分享越权防护：
     * 校验 targetId 是否位于分享根 rootId 之下（含 root 自身）。
     *
     * 适用场景：
     * - loadFileList：校验 filePid 是否在 root 下
     * - getFile / createDownloadUrl / preview：校验 fileId 是否在 root 下
     *
     * @param rootId   分享根文件/目录 id（分享时的 fileId）
     * @param shareUserId 分享者 userId（用于限定文件归属）
     * @param targetId 需要校验的文件/目录 id（fileId 或 filePid）
     */
    @Override
    public void checkInShareRoot(String rootId, String shareUserId, String targetId) {
        Map<String, FileInfo> cache = new HashMap<>();
        boolean ok = tree.isInSubtreeActive(shareUserId, rootId, targetId, cache);
        if (!ok) throw new BusinessException(ResponseCodeEnum.NO_PERMISSION);
    }

    @Override
    public PaginationResultVo<FileInfoVo> pageShareFiles(Integer pageNo, Integer pageSize, String filePid, ShareAccessDto access) {
        String pid = StringUtils.hasText(filePid) ? filePid : Constants.ROOT_PID;
        // 初始进入分享根目录（filePid == ROOT）无需校验
        if (!Constants.ROOT_PID.equals(pid))  // 校验当前目录必须位于分享根下，防止越权访问
            checkInShareRoot(access.getFileId(), access.getShareUserId(), pid);
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(access.getShareUserId());
        query.setDelFlag(FileDelFlagEnum.ACTIVE.getFlag()); // 你项目里的 USING
        if (!Constants.ROOT_PID.equals(pid))  // 列出指定目录下的子文件
            query.setFilePid(pid);
        else query.setFileId(access.getFileId());  // 列出分享的那个文件或目录本身
        QFileInfo f = QFileInfo.fileInfo;
        BooleanBuilder where = FileInfoQueryDsl.buildPredicate(query);
        List<OrderSpecifier<?>> orders = FileInfoQueryDsl.buildOrderSpecifiers(query);
        return queryDSLUtils.findPageByParam(
                f,
                where,
                pageNo,
                pageSize,
                orders,
                FileInfoVo.selectBase(f)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveShareFiles(String shareRootFileId,
                               String shareFileIds,
                               String myFolderId,
                               String shareUserId,
                               String currentUserId) {
        if (Objects.equals(shareUserId, currentUserId))
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        // 1) 解析 + 去重
        List<String> rootIdList = StringTools.parseDelimitedDistinctList(shareFileIds, ",");
        if (rootIdList.isEmpty())
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

        // 2) 安全校验：每个入口都必须在分享根下（防越权转存）
        for (String rootId : rootIdList)
            checkInShareRoot(shareRootFileId, shareUserId, rootId);

        // 3) 查出“被选中的节点”（只允许 USING）
        List<FileInfo> roots = fileInfoRepository.findByUserIdAndDelFlagAndFileIdIn(
                shareUserId, FileDelFlagEnum.ACTIVE.getFlag(), rootIdList
        );
        if (roots.isEmpty())
            throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
        if (roots.size() != rootIdList.size())
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

        // 4) 目标目录校验（根目录放行；非根目录必须存在且是目录且 USING）
        if (!Constants.ROOT_PID.equals(myFolderId)) {
            FileInfo targetFolder = fileInfoRepository.findByFileIdAndUserIdAndDelFlag(
                    myFolderId, currentUserId, FileDelFlagEnum.ACTIVE.getFlag()
            );
            if (targetFolder == null
                    || !FileFolderTypeEnum.FOLDER.getType().equals(targetFolder.getFolderType()))
                throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        }

        // 5) BFS 找所有子孙节点
        List<FileInfo> children = tree.collectDescendantsBfs(
                shareUserId,
                rootIdList,
                List.of(FileDelFlagEnum.ACTIVE.getFlag())
        );

        // 6) 目标目录已占用名称集合（只看 USING）
        Set<String> occupiedNames = new HashSet<>(
                fileInfoRepository.findFileNameByUserIdAndFilePidAndDelFlag(
                        currentUserId, myFolderId, FileDelFlagEnum.ACTIVE.getFlag()
                )
        );

        // 7) 构建 allNodes = roots + children
        List<FileInfo> allNodes = new ArrayList<>(roots.size() + children.size());
        allNodes.addAll(roots);
        allNodes.addAll(children);

        // 8) oldId -> newId 映射（保证父子结构）
        Map<String, String> idMap = new HashMap<>(allNodes.size());
        for (FileInfo n : allNodes)
            idMap.put(n.getFileId(), StringTools.getRandomString(Constants.RANDOM_FILE_ID_LENGTH));

        // 9) 创建所有 copy（但 pid 要按映射设置）
        List<FileInfo> copies = new ArrayList<>(allNodes.size());

        // roots 做重名并挂到 myFolderId
        Set<String> rootIds = roots.stream()
                .map(FileInfo::getFileId)
                .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        for (FileInfo n : allNodes) {
            FileInfo copy = fileInfoMapper.copy(n);
            copy.setUserId(currentUserId);
            copy.setFileId(idMap.get(n.getFileId()));
            copy.setCreatedAt(now);
            copy.setLastModifiedAt(now);
            copy.setDelFlag(FileDelFlagEnum.ACTIVE.getFlag());

            if (rootIds.contains(n.getFileId())) {  // 根节点
                // 顶层挂目标目录 + 重名
                String newName = StringTools.resolveConflict(n.getFileName(), occupiedNames);
                copy.setFileName(newName);
                copy.setFilePid(myFolderId);
            } else {  // 子孙节点：挂到“新父节点”下面
                String newPid = idMap.get(n.getFilePid());
                if (newPid == null)
                    // 防御：理论上 children 一定在 roots 子树里，若出现说明数据异常
                    throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
                copy.setFilePid(newPid);
            }
            copies.add(copy);
        }

        // 10) 计算总空间：只统计“文件”的 size
        long totalSize = copies.stream()
                .filter(n -> Objects.equals(n.getFolderType(), FileFolderTypeEnum.FILE.getType()))
                .map(FileInfo::getFileSize)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        // 11) 原子占用空间（并发安全，失败直接抛 STORAGE_INSUFFICIENT）
        if (totalSize > 0) userInfoService.addUsedSpace(currentUserId, totalSize);
        fileInfoRepository.saveAll(copies);
    }

    private Map<String, List<String>> parseUserIdFileIds(String input) {
        if (!StringUtils.hasText(input)) return Collections.emptyMap();

        Map<String, List<String>> map = new HashMap<>();

        String[] parts = input.split(",");
        for (String part : parts) {
            if (part == null) continue;
            String s = part.trim();
            if (s.isEmpty()) continue;

            int idx = s.indexOf('_');
            if (idx <= 0 || idx >= s.length() - 1) {
                // 这里你可以选择：跳过 or 抛业务异常
                throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
            }

            String userId = s.substring(0, idx).trim();
            String fileId = s.substring(idx + 1).trim();

            if (userId.isEmpty() || fileId.isEmpty()) {
                throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
            }

            map.computeIfAbsent(userId, k -> new ArrayList<>()).add(fileId);
        }

        // 去重
        map.replaceAll((k, v) -> v.stream().distinct().toList());
        return map;
    }

    @Override
    public String resolveBaseFolderForTsByDb(String realFileId, String userId) {

        FileInfo mine = fileInfoRepository.findByFileIdAndUserIdAndDelFlag(
                realFileId, userId, FileDelFlagEnum.ACTIVE.getFlag()
        );
        if (mine != null) {
            String fp = mine.getFilePath();
            if (!StringUtils.hasText(fp)) throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
            return StringTools.removeSuffix(fp);
        }

        String sharedFilePath = fileInfoRepository.findReadableShareFilePathForTs(realFileId, userId, FileDelFlagEnum.ACTIVE.getFlag());
        if (!StringUtils.hasText(sharedFilePath)) throw new BusinessException(ResponseCodeEnum.NO_PERMISSION);

        return StringTools.removeSuffix(sharedFilePath);
    }
}
