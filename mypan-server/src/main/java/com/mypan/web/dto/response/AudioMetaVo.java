package com.mypan.web.dto.response;

import lombok.Data;

import java.io.Serializable;

@Data
public final class AudioMetaVo implements Serializable {
    private String title;
    private String artist;
    private String album;
    private String year;
    private String genre;
    private String track;
    private Long bitrateKbps;
    private Integer sampleRateHz;
    private Integer bitDepth;
    private Integer durationSec;
    private Boolean isLossless;
}
