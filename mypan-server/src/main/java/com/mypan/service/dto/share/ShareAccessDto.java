package com.mypan.service.dto.share;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShareAccessDto implements Serializable {
    private String shareUserId;
    private String fileId;
    private Long expiredAt;
}
