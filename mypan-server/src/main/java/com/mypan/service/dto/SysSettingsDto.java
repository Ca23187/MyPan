package com.mypan.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SysSettingsDto implements Serializable {
    private String registerEmailTitle = "Welcome to MyPan";

    private String registerEmailContent = """
        <div style="font-family: Arial, sans-serif; font-size: 14px; color: #333;">
            <p>Hello,</p>

            <p>You are registering for <b>MyPan</b>.</p>

            <p>Your email verification code is:</p>

            <p style="font-size: 20px; font-weight: bold; color: #1a73e8;">
                %s
            </p>

            <p>This code is valid for 15 minutes.</p>

            <p>If you did not request this, please ignore this email.</p>
        </div>
        """;
    private Integer userInitTotalSpace = 5;
}
