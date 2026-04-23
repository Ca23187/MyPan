package com.mypan.web.dto.request;


import com.mypan.common.constants.VerifyRegex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public final class ResetPwdDto {

    @NotBlank(message = "Email cannot be blank.")
    @Email(message = "Invalid email format.")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Password cannot be blank.")
    @Size(min = 8, max = 18)
    @Pattern(regexp = VerifyRegex.PASSWORD, message = "Only numbers, letters, and special characters; 8 to 18 characters in length.")
    private String password;

    @NotBlank(message = "Captcha cannot be blank.")
    private String checkCode;

    @NotBlank(message = "Email code cannot be blank.")
    private String emailCode;

    @NotBlank(message = "Captcha key cannot be blank.")
    private String checkCodeKey;
}