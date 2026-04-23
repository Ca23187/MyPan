package com.mypan.web.dto.query;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileShareQuery extends BaseQuery{

    private String userId;

    private String shareId;
    private String fileId;

    private Integer expireType;
    private String code;
    private String codeFuzzy;
    private Integer folderType;
    private Integer fileCategory;
    private Integer fileType;

    private Boolean withFileInfo;

    private String expiredAtStart;
    private String expiredAtEnd;
    private String sharedAtStart;
    private String sharedAtEnd;

}
