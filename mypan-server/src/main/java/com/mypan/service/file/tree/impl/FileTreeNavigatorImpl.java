package com.mypan.service.file.tree.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileDelFlagEnum;
import com.mypan.common.enums.FileFolderTypeEnum;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.infra.jpa.repository.FileInfoRepository;
import com.mypan.service.file.tree.FileTreeNavigator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FileTreeNavigatorImpl implements FileTreeNavigator {

    private final FileInfoRepository fileInfoRepository;

    @Override
    public FileInfo getActiveNode(String userId, String fileId, Map<String, FileInfo> cache) {
        if (!StringUtils.hasText(fileId) || Constants.ROOT_PID.equals(fileId)) return null;
        if (cache != null && cache.containsKey(fileId)) return cache.get(fileId);

        FileInfo node = fileInfoRepository.findByFileIdAndUserIdAndDelFlag(
                fileId, userId, FileDelFlagEnum.ACTIVE.getFlag()
        );

        if (cache != null) cache.put(fileId, node); // 允许缓存 null
        return node;
    }

    @Override
    public List<FileInfo> buildActiveAncestorChain(String userId,
                                                   String startId,
                                                   boolean includeSelf,
                                                   Map<String, FileInfo> cache) {
        if (!StringUtils.hasText(startId) || Constants.ROOT_PID.equals(startId)) return List.of();

        List<FileInfo> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>(); // 防脏数据死循环

        String curPid;

        if (includeSelf) {
            FileInfo self = getActiveNode(userId, startId, cache);
            if (self == null) return List.of();
            chain.add(self);
            curPid = self.getFilePid();
        } else {
            FileInfo node = getActiveNode(userId, startId, cache);
            if (node == null) return List.of();
            curPid = node.getFilePid();
        }

        while (StringUtils.hasText(curPid) && !Constants.ROOT_PID.equals(curPid)) {
            if (!seen.add(curPid)) break; // cycle
            FileInfo parent = getActiveNode(userId, curPid, cache);
            if (parent == null) break;
            chain.add(parent);
            curPid = parent.getFilePid();
        }

        return chain;
    }

    @Override
    public boolean isInSubtreeActive(String userId,
                                     String rootId,
                                     String targetId,
                                     Map<String, FileInfo> cache) {
        if (!StringUtils.hasText(rootId) || !StringUtils.hasText(targetId))
            return false;
        if (Objects.equals(rootId, targetId))
            return true;

        Set<String> seen = new HashSet<>();
        String cur = targetId;
        while (StringUtils.hasText(cur) && !Constants.ROOT_PID.equals(cur)) {
            if (!seen.add(cur)) break;
            FileInfo node = getActiveNode(userId, cur, cache);
            if (node == null) return false;
            String pid = node.getFilePid();
            if (!StringUtils.hasText(pid)) return false;
            if (Objects.equals(pid, rootId)) return true;
            cur = pid;
        }
        return false;
    }

    @Override
    public List<FileInfo> collectDescendantsBfs(String userId,
                                                Collection<String> rootIds,
                                                Collection<Integer> delFlags) {
        if (rootIds == null || rootIds.isEmpty()) return List.of();
        List<Integer> flags = delFlags == null ? List.of(FileDelFlagEnum.ACTIVE.getFlag())
                : delFlags.stream().distinct().toList();

        // frontier = 待展开 folderId（只展开目录）
        Set<String> frontier = new HashSet<>(rootIds);

        Set<String> seen = new HashSet<>();
        List<FileInfo> acc = new ArrayList<>();

        while (!frontier.isEmpty()) {
            List<String> batchAll = new ArrayList<>(frontier);
            frontier.clear();

            for (int i = 0; i < batchAll.size(); i += Constants.BFS_BATCH_SIZE) {
                List<String> pidBatch = batchAll.subList(i, Math.min(i + Constants.BFS_BATCH_SIZE, batchAll.size()));

                List<FileInfo> children = fileInfoRepository
                        .findByUserIdAndFilePidInAndDelFlagIn(userId, pidBatch, flags);

                for (FileInfo c : children) {
                    if (!seen.add(c.getFileId())) continue;
                    acc.add(c);
                    if (Objects.equals(c.getFolderType(), FileFolderTypeEnum.FOLDER.getType())) {
                        frontier.add(c.getFileId());
                    }
                }
            }
        }
        return acc;
    }

    @Override
    public List<String> collectFolderIdsBfs(String userId,
                                            String rootFolderId,
                                            int delFlag,
                                            boolean includeRoot) {
        if (!StringUtils.hasText(rootFolderId))
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

        FileInfo root = fileInfoRepository.findByFileIdAndUserIdAndDelFlag(
                rootFolderId, userId, delFlag
        );
        if (root == null || !Objects.equals(root.getFolderType(), FileFolderTypeEnum.FOLDER.getType()))
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);

        Set<String> out = new LinkedHashSet<>();
        Set<String> frontier = new HashSet<>();

        if (includeRoot) out.add(rootFolderId);
        frontier.add(rootFolderId);

        while (!frontier.isEmpty()) {
            List<String> batchAll = new ArrayList<>(frontier);
            frontier.clear();

            for (int i = 0; i < batchAll.size(); i += Constants.BFS_BATCH_SIZE) {
                List<String> pidBatch = batchAll.subList(i, Math.min(i + Constants.BFS_BATCH_SIZE, batchAll.size()));

                List<FileInfo> children = fileInfoRepository
                        .findByUserIdAndFilePidInAndDelFlag(userId, pidBatch, delFlag);

                for (FileInfo c : children) {
                    if (!Objects.equals(c.getFolderType(), FileFolderTypeEnum.FOLDER.getType())) continue;
                    if (out.add(c.getFileId())) frontier.add(c.getFileId());
                }
            }
        }

        return new ArrayList<>(out);
    }
}
