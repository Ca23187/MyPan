package com.mypan.service.user.impl;

import com.mypan.common.constants.Constants;
import com.mypan.common.exception.BusinessException;
import com.mypan.common.response.ResponseCodeEnum;
import com.mypan.common.utils.string.StringTools;
import com.mypan.config.AppProperties;
import com.mypan.infra.jpa.repository.UserInfoRepository;
import com.mypan.infra.redis.RedisComponent;
import com.mypan.infra.redis.RedisUtils;
import com.mypan.service.dto.SysSettingsDto;
import com.mypan.service.user.EmailCodeService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailCodeServiceImpl implements EmailCodeService {

    private final UserInfoRepository userInfoRepository;

    private final JavaMailSender javaMailSender;

    private final AppProperties appProperties;

    private final RedisComponent redisComponent;

    private final RedisUtils redisUtils;

    @Override
    public void sendEmail(String email, Integer type) {
        if (type.equals(Constants.EMAIL_CODE_TYPE_REGISTER)) {
            boolean exists = userInfoRepository.existsByEmail(email);
            if (exists)
                throw new BusinessException(ResponseCodeEnum.EMAIL_ALREADY_EXISTS);
        }

        // 生成验证码
        String code = StringTools.getRandomNumber(Constants.EMAIL_CODE_LENGTH);

        // 读取系统配置（标题、模板）
        SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();
        String subject = sysSettingsDto.getRegisterEmailTitle();
        String content = String.format(sysSettingsDto.getRegisterEmailContent(), code);

        // 发送邮件
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(appProperties.getMail().getFrom());
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(content, true);
            helper.setSentDate(new Date());
            javaMailSender.send(message);
        } catch (MessagingException e) {
            log.error("邮件发送失败，email={}", email, e);
            throw new BusinessException(ResponseCodeEnum.INTERNAL_ERROR);
        }

        // 保存验证码到 Redis
        String redisKey = Constants.REDIS_KEY_EMAIL_CODE + email;
        redisComponent.saveEmailCode(redisKey, code);
    }

    @Override
    public void checkCode(String redisKey, String checkCode, boolean isCaptcha) {
        String scene = isCaptcha ? "Captcha" : "Email code";

        String realCode = redisUtils.get(redisKey);
        if (realCode == null)
            throw new BusinessException(scene + " has expired. Please obtain a new one.");

        if (!checkCode.trim().equalsIgnoreCase(realCode)) {
            if (isCaptcha) redisUtils.delete(redisKey);
            throw new BusinessException(scene + " is incorrect.");
        }
    }
}
