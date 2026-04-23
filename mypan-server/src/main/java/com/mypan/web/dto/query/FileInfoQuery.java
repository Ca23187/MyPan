package com.mypan.web.dto.query;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FileInfoQuery extends BaseQuery{

    private String userId;
    private Integer delFlag;
    private String filePid;
    private List<String> filePidIn;

    private String fileId;
    private List<String> fileIdIn;
    private List<String> excludeFileIdIn;
    private List<String> excludeUserIdIn;

    private String fileNameFuzzy;
    private String nicknameFuzzy;

    private Integer folderType;
    private Integer fileCategory;
    private Integer fileType;
    private Integer status;
    private Integer excludeDelFlag;

    private String createdAtStart;
    private String createdAtEnd;
    private String lastModifiedAtStart;
    private String lastModifiedAtEnd;
    private String recycledAtStart;
    private String recycledAtEnd;

    private Integer searchScope;
}

