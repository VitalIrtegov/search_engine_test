package searchengine.models;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    private String userAgent;
    private String referrer;
    private Delay delay;
    private int timeout;

    @Data
    public static class Delay {
        private int min;
        private int max;
    }
}

