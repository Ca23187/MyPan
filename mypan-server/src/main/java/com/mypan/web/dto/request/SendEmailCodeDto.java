package com.mypan.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public final class SendEmailCodeDto {

    @NotBlank(message = "Email cannot be blank.")
    @Email(message = "Invalid email format.")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Captcha cannot be blank.")
    private String checkCode;

    @NotNull(message = "Type cannot be blank.")
    private Integer type;

    @NotBlank(message = "Captcha key cannot be blank.")
    private String checkCodeKey;
}
