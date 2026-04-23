package com.mypan.service.user;

public interface EmailCodeService {
    void sendEmail(String email, Integer type);
    void checkCode(String redisKey, String checkCode, boolean isCaptcha);
}
