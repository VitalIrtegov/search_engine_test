package searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "crawler")
public class ConfigCrawler {
    private String userAgent;
    private String referrer;
    private DelayConfig delay;
    private int timeout;

    @Data
    public static class DelayConfig {
        private int min;
        private int max;
    }
}
