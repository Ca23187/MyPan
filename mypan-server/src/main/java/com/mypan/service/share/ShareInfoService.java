package com.mypan.service.share;

import com.mypan.infra.jpa.entity.FileShare;
import com.mypan.service.dto.share.ShareAccessDto;
import com.mypan.web.dto.query.FileShareQuery;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.share.FileShareVo;
import com.mypan.web.dto.response.share.ShareInfoVo;

public interface ShareInfoService {
    PaginationResultVo<FileShareVo> pageMyShares(String userId, FileShareQuery query);

    FileShare createShare(String userId, String fileId, Integer expireType, String code);

    void deleteShares(String shareIds, String userId);

    ShareInfoVo getShareInfoVo(String shareId);

    ShareAccessDto checkShareCode(String shareId, String code);
}
