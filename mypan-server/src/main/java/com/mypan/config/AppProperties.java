package com.mypan.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String projectFolder;
    private boolean dev;
    private List<String> adminIds = new ArrayList<>();
    private final Mail mail = new Mail();
    private final Qq qq = new Qq();

    @Getter
    @Setter
    public static class Mail {
        /** 发件人 */
        private String from;
    }

    @Getter
    @Setter
    public static class Qq {
        private String appId;
        private String appKey;
        private String urlAuthorization;
        private String urlAccessToken;
        private String urlOpenId;
        private String urlUserInfo;
        private String urlRedirect;
    }
}
