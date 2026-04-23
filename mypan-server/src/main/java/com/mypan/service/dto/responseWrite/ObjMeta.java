package com.mypan.service.dto.responseWrite;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public final class ObjMeta {
    private Long size;
    private String contentType;
}
