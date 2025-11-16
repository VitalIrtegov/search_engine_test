package searchengine.dto.site;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class AddSiteRequest {
    @NotBlank(message = "Site name cannot be empty")
    @Size(min = 2, max = 255, message = "Site name must be between 2 and 255 characters")
    @Pattern(regexp = "^[\\w\\s\\-\\.\\,\\(\\)\\&\\+а-яА-Я]+$",
            message = "Site name contains invalid characters. Only letters (including Russian), numbers, spaces, and basic punctuation allowed")
    private String name;

    @NotBlank(message = "URL cannot be empty")
    private String url;
}
