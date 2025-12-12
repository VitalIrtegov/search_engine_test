package searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "crawler")
public class ConfigIndexing {
    private String userAgent;
    private String referrer;
    private DelayConfig delay;
    private int timeout;
    private int batch_pause;

    @Data
    public static class DelayConfig {
        private int min;
        private int max;
    }
}
