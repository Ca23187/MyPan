package com.mypan.service.share.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.enums.FileDelFlagEnum;
import com.mypan.common.enums.ShareExpireTypeEnum;
import com.mypan.common.enums.UserStatusEnum;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.string.StringTools;
import com.mypan.common.utils.time.DateUtils;
import com.mypan.infra.jpa.entity.FileShare;
import com.mypan.infra.jpa.entity.QFileInfo;
import com.mypan.infra.jpa.entity.QFileShare;
import com.mypan.infra.jpa.querydsl.file.FileShareQueryDsl;
import com.mypan.infra.jpa.querydsl.support.QueryDslUtils;
import com.mypan.infra.jpa.repository.FileShareRepository;
import com.mypan.infra.mapstruct.ShareInfoDtoMapper;
import com.mypan.service.dto.share.ShareAccessDto;
import com.mypan.service.dto.share.ShareInfoDto;
import com.mypan.service.share.ShareInfoService;
import com.mypan.web.dto.query.FileShareQuery;
import com.mypan.web.dto.response.PaginationResultVo;
import com.mypan.web.dto.response.share.FileShareVo;
import com.mypan.web.dto.response.share.ShareInfoVo;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ShareInfoServiceImpl implements ShareInfoService {

    private final FileShareRepository repo;

    private final QueryDslUtils queryDslUtils;

    private final ShareInfoDtoMapper shareInfoDtoMapper;

    @Override
    public PaginationResultVo<FileShareVo> pageMyShares(String userId, FileShareQuery query) {
        query.setUserId(userId);
        BooleanBuilder where = FileShareQueryDsl.buildPredicate(query);
        List<OrderSpecifier<?>> orders = FileShareQueryDsl.buildOrderSpecifiers(query);
        QFileShare s = QFileShare.fileShare;
        QFileInfo f = QFileInfo.fileInfo;
        return queryDslUtils.findPageByParamWithJoin(
                s,
                where,
                query.getPageNo(),
                query.getPageSize(),
                orders,
                FileShareVo.selectWithFileInfo(s, f),
                s.shareId,
                q -> q.leftJoin(f)
                        .on(s.fileId.eq(f.fileId)
                                .and(f.userId.eq(userId))
                        )
        );
    }

    @Override
    public FileShare createShare(String userId, String fileId, Integer expireType, String code) {
        ShareExpireTypeEnum typeEnum = ShareExpireTypeEnum.getByType(expireType);
        if (null == typeEnum)
            throw new BusinessException(ResponseCodeEnum.BAD_REQUEST);
        FileShare share = new FileShare();
        share.setShareId(StringTools.getRandomString(Constants.SHARE_ID_LENGTH));
        share.setUserId(userId);
        share.setFileId(fileId);
        share.setExpireType(expireType);
        share.setViewCount(0);
        if (typeEnum != ShareExpireTypeEnum.FOREVER)
            share.setExpiredAt(DateUtils.getAfterDateTime(typeEnum.getDays()));
        share.setCode(StringUtils.hasText(code)
                ? code.trim()
                : StringTools.getRandomString(Constants.SHARE_CODE_LENGTH));
        repo.save(share);
        return share;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShares(String shareIds, String userId) {
        List<String> shareIdList = StringTools.parseDelimitedDistinctList(shareIds, ",");
        if (shareIdList.isEmpty())
            throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
        Integer count = repo.deleteByUserIdAndShareIdIn(userId, shareIdList);
        if (count != shareIdList.size())
            throw new BusinessException(ResponseCodeEnum.FILE_NOT_FOUND);
    }

    @Override
    public ShareInfoVo getShareInfoVo(String shareId) {
        return shareInfoDtoMapper.toVo(getShareInfoDto(shareId));
    }

    private ShareInfoDto getShareInfoDto(String shareId) {
        ShareInfoDto dto = repo.getShareInfoDto(shareId);
        if (dto == null)
            throw new BusinessException(ResponseCodeEnum.SHARE_NOT_FOUND);
        if (!Objects.equals(dto.getDelFlag(), FileDelFlagEnum.ACTIVE.getFlag()))
            throw new BusinessException(ResponseCodeEnum.SHARE_DELETED);
        if (dto.getExpiredAt() != null && LocalDateTime.now().isAfter(dto.getExpiredAt()))
            throw new BusinessException(ResponseCodeEnum.SHARE_EXPIRED);
        if (!(Objects.equals(dto.getUserStatus(), UserStatusEnum.ACTIVE.getStatus())))
            throw new BusinessException(ResponseCodeEnum.SHARE_OWNER_BANNED);
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShareAccessDto checkShareCode(String shareId, String code) {
        ShareInfoDto dto = getShareInfoDto(shareId);
        if (!Objects.equals(dto.getShareCode(), code))
            throw new BusinessException("Code is incorrect.");
        repo.incrViewCount(shareId);
        return shareInfoDtoMapper.toAccessDto(dto);
    }
}
