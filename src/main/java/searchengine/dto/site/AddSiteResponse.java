package searchengine.dto.site;

import lombok.Data;

@Data
public class AddSiteResponse {
    private boolean result;
    private String message;
    private Integer siteId;
}
