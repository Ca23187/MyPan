package com.mypan.infra.mapstruct;

import com.mypan.common.utils.time.DateUtils;
import com.mypan.service.dto.share.ShareAccessDto;
import com.mypan.service.dto.share.ShareInfoDto;
import com.mypan.web.dto.response.share.ShareInfoVo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateUtils.class)
//  NOTE: DateUtils 里有个 static 方法 toMilli，
//   如果 source 和 target 有相同名称字段且前者LocalDateTime后者Date就能自动转换
public interface ShareInfoDtoMapper {
    ShareInfoVo toVo(ShareInfoDto dto);
    @Mapping(target = "shareUserId", source = "userId")
    ShareAccessDto toAccessDto(ShareInfoDto dto);
}