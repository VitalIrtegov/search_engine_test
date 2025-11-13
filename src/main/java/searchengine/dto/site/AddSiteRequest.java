package searchengine.dto.site;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class AddSiteRequest {
    @NotBlank(message = "Site name cannot be empty")
    @Size(min = 2, max = 255, message = "Site name must be between 2 and 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-\\.\\,\\(\\)\\&\\+]+$",
            message = "Site name contains invalid characters. Only letters, numbers, spaces, and basic punctuation allowed")
    private String name;

    @NotBlank(message = "URL cannot be empty")
    @Pattern(regexp = "^(https)://([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(:\\d+)?(/.*)?$",
            message = "Invalid URL format. Must start with https:// and be a valid domain name")
    private String url;
}
