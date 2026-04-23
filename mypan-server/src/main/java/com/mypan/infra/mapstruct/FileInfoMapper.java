package com.mypan.infra.mapstruct;

import com.mypan.infra.jpa.entity.FileInfo;
import com.mypan.web.dto.response.file.FileInfoVo;
import com.mypan.web.dto.response.file.FolderVo;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FileInfoMapper {
    FileInfo copy(FileInfo fileInfo);
    FileInfoVo toVo(FileInfo fileInfo);
    FolderVo toFolderVo(FileInfo fileInfo);
}
