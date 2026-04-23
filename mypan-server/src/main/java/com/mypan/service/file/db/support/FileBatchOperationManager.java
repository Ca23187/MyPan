package com.mypan.service.file.db.support;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileDelFlagEnum;
import com.mypan.common.enums.FileFolderTypeEnum;
import com.mypan.common.enums.FileStatusEnum;
import com.mypan.common.utils.string.StringTools;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.jpa.repository.FileInfoRepository;
import com.mypan.service.dto.download.ZipEntryResource;
import com.mypan.service.file.tree.FileTreeNavigator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 批量操作增强段管理器：
 * - Impl 里保留传统（同级）逻辑
 * - 分水岭处仅在“跨级”时调用这里的增强逻辑（例如父子过滤）
 */
@Component
@RequiredArgsConstructor
public class FileBatchOperationManager {

    private final FileInfoRepository fileInfoRepository;
    private final FileTreeNavigator tree;

    /**
     * 父子过滤：若某项在“被选中目录”的子树里，则剔除（只保留最顶层选中项）
     *
     * 说明：
     * - 搜索页常见：同时选中 A目录 和 A/子文件。此时只需要移动 A目录。
     * - 这里通过“向上追溯父链”判断某 item 是否被某个选中目录包含。
     *
     * @param selected        本次要移动的节点（文件/目录混合）
     * @param userId          用户 id
     * @param movingFolderIds 本次选中的“目录”id 集合
     * @param parentCache     本次请求内缓存：避免父链追溯重复查 DB（校验段和过滤段可共享）
     * @return 过滤后的 selected（新 List）
     */
    public List<FileInfo> filterTopLevelSelections(List<FileInfo> selected,
                                                   String userId,
                                                   Set<String> movingFolderIds,
                                                   Map<String, FileInfo> parentCache) {
        if (selected == null || selected.isEmpty())
            return List.of();
        if (movingFolderIds == null || movingFolderIds.isEmpty())
            return selected;

        List<FileInfo> result = new ArrayList<>(selected.size());
        for (FileInfo item : selected) {
            if (movingFolderIds.contains(item.getFileId())) { result.add(item); continue; }

            boolean contained = false;
            String curPid = item.getFilePid();
            while (StringUtils.hasText(curPid) && !Constants.ROOT_PID.equals(curPid)) {
                if (movingFolderIds.contains(curPid)) { contained = true; break; }
                FileInfo parent = tree.getActiveNode(userId, curPid, parentCache);
                if (parent == null) break;
                curPid = parent.getFilePid();
            }

            if (!contained) result.add(item);
        }
        return result;
    }

    // =========================
    // 对外方法：同级 / 跨级
    // =========================

    /** 同级 roots：zip 根目录直接追加 roots（BFS 批量展开目录） */
    public List<ZipEntryResource> buildZipEntriesSameLevelBfs(String userId, List<FileInfo> roots) {
        Map<String, Set<String>> usedNamesByDir = new HashMap<>();
        usedNamesByDir.put("", new HashSet<>());

        // folderId -> zipPrefix
        Map<String, String> zipPrefixByFolderId = new HashMap<>();

        List<ZipEntryResource> out = new ArrayList<>();
        // roots 本身放到 zip 根目录
        Set<String> frontier = addRootsToZipAndEnqueueFolders(userId, roots, "", usedNamesByDir, zipPrefixByFolderId, out);

        // 批量 BFS 展开所有入队的目录
        expandFoldersBfsBatch(userId, usedNamesByDir, zipPrefixByFolderId, frontier, out);

        return out;
    }

    /** 跨级 roots：先复刻父链目录得到 prefix，再追加 roots（BFS 批量展开目录） */
    public List<ZipEntryResource> buildZipEntriesMixedLevelBfs(String userId, List<FileInfo> roots) {
        Map<String, Set<String>> usedNamesByDir = new HashMap<>();
        usedNamesByDir.put("", new HashSet<>());

        Map<String, String> zipPrefixByFolderId = new HashMap<>();
        Map<String, FileInfo> parentCache = new HashMap<>();

        List<ZipEntryResource> out = new ArrayList<>();
        Set<String> frontier = new HashSet<>();

        for (FileInfo root : roots) {
            // 1) 先为 root 的父链生成 zip 前缀
            String prefix = ensureZipPrefixForParentPath(
                    userId,
                    root.getFilePid(),
                    usedNamesByDir,
                    zipPrefixByFolderId,
                    parentCache
            );

            // 2) 把 root 节点放到对应 prefix 下，并入队（如果是目录）
            frontier.addAll(
                    addRootsToZipAndEnqueueFolders(userId, List.of(root), prefix, usedNamesByDir, zipPrefixByFolderId, out)
            );
        }

        // 3) 批量 BFS 展开入队目录
        if (!frontier.isEmpty()) {
            expandFoldersBfsBatch(userId, usedNamesByDir, zipPrefixByFolderId, frontier, out);
        }
        return out;
    }

    // =========================
    // BFS 核心：先处理 roots，再批量展开 folders
    // =========================

    /**
     * 处理一批 roots：
     * - 在 dirPrefix 这一层做同层重名消解
     * - 文件：直接产出 ZipEntryResource
     * - 目录：计算 newPrefix，记录到 zipPrefixByFolderId，并加入待展开队列（通过 zipPrefixByFolderId 的 keySet 作为“待展开池”）
     */
    private Set<String> addRootsToZipAndEnqueueFolders(String userId,
                                                List<FileInfo> roots,
                                                String dirPrefix,
                                                Map<String, Set<String>> usedNamesByDir,
                                                Map<String, String> zipPrefixByFolderId,
                                                List<ZipEntryResource> out) {
        if (roots == null || roots.isEmpty()) return Set.of();
        Set<String> enqueued = new HashSet<>();
        for (FileInfo node : roots) {
            // 只看 USING
            if (!Objects.equals(node.getDelFlag(), FileDelFlagEnum.ACTIVE.getFlag())) continue;

            Set<String> usedHere = usedNamesByDir.computeIfAbsent(dirPrefix, k -> new HashSet<>());
            String safeName = StringTools.resolveConflict(node.getFileName(), usedHere);

            if (Objects.equals(node.getFolderType(), FileFolderTypeEnum.FILE.getType())) {

                // 文件只打包 status=USING
                if (!Objects.equals(node.getStatus(), FileStatusEnum.ACTIVE.getStatus())) continue;

                ZipEntryResource e = new ZipEntryResource();
                e.setObjectKey(node.getFilePath());
                e.setEntryName(dirPrefix + safeName);
                out.add(e);
                continue;
            }

            // 目录：记录它在 zip 中的前缀，并标记为待展开
            String folderId = node.getFileId();
            String newPrefix = dirPrefix + safeName + "/";
            usedNamesByDir.computeIfAbsent(newPrefix, k -> new HashSet<>());

            // ✅ 写目录 entry，保证空文件夹可见
            out.add(ZipEntryResource.dir(newPrefix));

            // 注意：同一个 folderId 可能多次出现（比如 root 重复），以第一次为准
            if (zipPrefixByFolderId.putIfAbsent(folderId, newPrefix) == null) {
                enqueued.add(folderId);
            }
        }
        return enqueued;
    }

    /**
     * 批量 BFS 展开 zipPrefixByFolderId 中的所有 folderId。
     *
     * 关键点：
     * - 一轮一轮按 folderId 批量查 children（filePid IN (...)）
     * - 每个 folderId 都有确定的 zipPrefix（从 zipPrefixByFolderId 取）
     * - 子目录计算 newPrefix 后写回 zipPrefixByFolderId，并进入后续轮次
     */
    private void expandFoldersBfsBatch(String userId,
                                       Map<String, Set<String>> usedNamesByDir,
                                       Map<String, String> zipPrefixByFolderId,
                                       Set<String> frontier,
                                       List<ZipEntryResource> out) {

        // frontier = 待展开的 folderId
        // expanded 用来防止重复展开（避免某目录被多次入队导致重复 children 查询）
        Set<String> expanded = new HashSet<>();

        while (!frontier.isEmpty()) {
            List<String> batchAll = new ArrayList<>(frontier);
            frontier.clear();

            for (int i = 0; i < batchAll.size(); i += Constants.BFS_BATCH_SIZE) {
                List<String> pidBatch = batchAll.subList(i, Math.min(i + Constants.BFS_BATCH_SIZE, batchAll.size()));

                // 过滤掉已经展开过的 pid（减少无用查询）
                List<String> toQuery = pidBatch.stream()
                        .filter(pid -> expanded.add(pid))
                        .toList();
                if (toQuery.isEmpty()) continue;

                // 一次查一批 pid 的 children（只查 USING）
                List<FileInfo> children = fileInfoRepository.findByUserIdAndFilePidInAndDelFlag(
                        userId, toQuery, FileDelFlagEnum.ACTIVE.getFlag()
                );

                // 按 parentId 分组
                Map<String, List<FileInfo>> childrenByPid = children.stream()
                        .collect(Collectors.groupingBy(FileInfo::getFilePid));

                // 逐个父目录处理其子节点
                for (String pid : toQuery) {
                    String parentPrefix = zipPrefixByFolderId.get(pid);
                    if (parentPrefix == null) {
                        // 理论不应发生：pid 进入 frontier 时必须有 prefix
                        parentPrefix = "";
                    }

                    List<FileInfo> cs = childrenByPid.getOrDefault(pid, List.of());
                    for (FileInfo c : cs) {
                        // 同层冲突消解：在 parentPrefix 层
                        Set<String> usedHere = usedNamesByDir.computeIfAbsent(parentPrefix, k -> new HashSet<>());
                        String safeName = StringTools.resolveConflict(c.getFileName(), usedHere);

                        if (Objects.equals(c.getFolderType(), FileFolderTypeEnum.FILE.getType())) {
                            if (!Objects.equals(c.getStatus(), FileStatusEnum.ACTIVE.getStatus())) continue;

                            ZipEntryResource e = new ZipEntryResource();
                            e.setObjectKey(c.getFilePath());
                            e.setEntryName(parentPrefix + safeName);
                            out.add(e);
                            continue;
                        }

                        // 子目录：计算其 prefix，入队
                        String childFolderId = c.getFileId();
                        String childPrefix = parentPrefix + safeName + "/";
                        usedNamesByDir.computeIfAbsent(childPrefix, k -> new HashSet<>());

                        // ✅ 写目录 entry，保证空文件夹可见
                        out.add(ZipEntryResource.dir(childPrefix));

                        // 如果这个 childFolderId 之前没有 prefix，才入队（避免重复入队）
                        if (zipPrefixByFolderId.putIfAbsent(childFolderId, childPrefix) == null) {
                            frontier.add(childFolderId);
                        }
                    }
                }
            }
        }
    }

    // =========================
    // mixedLevel 复刻父链：复用你现有逻辑（保持行为一致）
    // =========================

    private String ensureZipPrefixForParentPath(String userId,
                                                String folderId,
                                                Map<String, Set<String>> usedNamesByDir,
                                                Map<String, String> zipPrefixByFolderId,
                                                Map<String, FileInfo> parentCache) {
        if (!StringUtils.hasText(folderId) || Constants.ROOT_PID.equals(folderId)) {
            return "";
        }
        if (zipPrefixByFolderId.containsKey(folderId)) {
            return zipPrefixByFolderId.get(folderId);
        }

        FileInfo folder = tree.getActiveNode(folderId, userId, parentCache);
        if (folder == null) {
            zipPrefixByFolderId.put(folderId, "");
            return "";
        }

        String parentPrefix = ensureZipPrefixForParentPath(
                userId, folder.getFilePid(), usedNamesByDir, zipPrefixByFolderId, parentCache
        );

        Set<String> usedHere = usedNamesByDir.computeIfAbsent(parentPrefix, k -> new HashSet<>());
        String safeName = StringTools.resolveConflict(folder.getFileName(), usedHere);

        String myPrefix = parentPrefix + safeName + "/";
        usedNamesByDir.computeIfAbsent(myPrefix, k -> new HashSet<>());
        zipPrefixByFolderId.putIfAbsent(folderId, myPrefix);

        return myPrefix;
    }
}