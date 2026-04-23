package com.mypan.web.dto.query;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UserInfoQuery extends BaseQuery {
	private String userId;

	private String nicknameFuzzy;
	private String emailFuzzy;

	private String qqOpenId;

	private Integer status;

	private String createdAtStart;
	private String createdAtEnd;
	private String lastLoginAtStart;
	private String lastLoginAtEnd;

	private Long usedSpaceMin;
	private Long usedSpaceMax;
	private Long totalSpaceMin;
	private Long totalSpaceMax;
}
