package com.mypan.service.file.tree;

import com.mypan.infra.jpa.entity.FileInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface FileTreeNavigator {

    /**
     * 读取一个 ACTIVE 节点（带请求内 cache，可缓存 null）
     */
    FileInfo getActiveNode(String userId, String fileId, Map<String, FileInfo> cache);

    /**
     * 从某节点一路向上找到 ROOT（只读 ACTIVE），返回链（包含自己，可选）
     * @param includeSelf 是否包含起点节点
     */
    List<FileInfo> buildActiveAncestorChain(String userId,
                                            String startId,
                                            boolean includeSelf,
                                            Map<String, FileInfo> cache);

    /**
     * 判断 targetId 是否在 rootId 子树内（含 root 自身）
     * 仅基于 ACTIVE 父链判断（分享越权/面包屑等）
     */
    boolean isInSubtreeActive(String userId,
                              String rootId,
                              String targetId,
                              Map<String, FileInfo> cache);

    /**
     * BFS 收集后代节点（可传 delFlags），不包含 roots 自己
     */
    List<FileInfo> collectDescendantsBfs(String userId,
                                         Collection<String> rootIds,
                                         Collection<Integer> delFlags);

    /**
     * BFS 收集“目录”后代的 folderId（可选择包含 root）
     */
    List<String> collectFolderIdsBfs(String userId,
                                     String rootFolderId,
                                     int delFlag,
                                     boolean includeRoot);
}
