package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.site.AddSiteRequest;
import searchengine.dto.site.AddSiteResponse;
import searchengine.dto.site.DeleteSiteResponse;
import searchengine.models.ConfigSite;
import searchengine.utils.UrlValidator;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteService {
    private final ConfigSiteService configSiteService;

    public AddSiteResponse addSite(AddSiteRequest request) {
        //log.info("Request - Name: '{}', URL: '{}'", request.getName(), request.getUrl());
        AddSiteResponse response = new AddSiteResponse();

        try {
            // Дополнительная валидация URL
            String validationError = UrlValidator.validateUrl(request.getUrl());
            if (validationError != null) {
                //log.warn("URL validation failed: {}", validationError);
                response.setResult(false);
                response.setMessage(validationError);
                return response;
            }
            //log.info("URL validation passed");

            // Проверка на дубликаты
            if (configSiteService.siteExistsByUrl(request.getUrl())) {
                //log.warn("Site with name already exists: {}", request.getName());
                response.setResult(false);
                response.setMessage("Site with this URL already exists");
                return response;
            }
            //log.info("Duplicate check passed");

            if (configSiteService.siteExistsByName(request.getName())) {
                response.setResult(false);
                response.setMessage("Site with this name already exists");
                return response;
            }

            // Нормализация URL
            String normalizedUrl = UrlValidator.normalizeUrl(request.getUrl());
            //log.info("Normalized URL: {}", normalizedUrl);

            // Создание и сохранение сайта
            ConfigSite site = new ConfigSite();
            site.setName(request.getName().trim());
            site.setUrl(normalizedUrl);

            //log.info("Saving site to database...");
            ConfigSite savedSite = configSiteService.saveSite(site);
            //log.info("Site saved successfully with ID: {}", savedSite.getId());

            // Проверим, действительно ли сохранилось
            boolean existsAfterSave = configSiteService.siteExistsByUrl(normalizedUrl);
            //log.info("Site exists after save: {}", existsAfterSave);

            response.setResult(true);
            response.setMessage("Site added successfully");
            response.setSiteId(savedSite.getId());

        } catch (Exception e) {
            //log.error("Error adding site: ", e);
            response.setResult(false);
            response.setMessage("Error adding site: " + e.getMessage());
        }

        return response;
    }

    public DeleteSiteResponse deleteSite(String siteName) {
        //log.info("Deleting site with name: {}", siteName);

        DeleteSiteResponse response = new DeleteSiteResponse();

        try {
            // Находим сайт по имени
            //log.info("Searching for site by name: '{}'", siteName);
            Optional<ConfigSite> siteOptional = configSiteService.getSiteByName(siteName);

            if (siteOptional.isEmpty()) {
                //log.warn("Site not found for name: '{}'", siteName);

                // Покажем все сайты для отладки
                /*List<ConfigSite> allSites = configSiteService.getAllSites();
                log.info("All sites in database ({} sites):", allSites.size());
                for (ConfigSite site : allSites) {
                    log.info(" - ID: {}, Name: '{}', URL: {}", site.getId(), site.getName(), site.getUrl());
                }*/

                response.setResult(false);
                response.setMessage("Site not found: " + siteName);
                return response;
            }

            ConfigSite site = siteOptional.get();
            //log.info("Found site to delete: ID={}, Name='{}', URL={}", site.getId(), site.getName(), site.getUrl());

            // Проверяем, что объект управляется Hibernate
            //log.info("Site entity state - ID: {}, Name: {}", site.getId(), site.getName());

            // Удаляем сайт
            //log.info("Calling deleteSite with ID: {}", site.getId());
            configSiteService.deleteSite(site.getId());
            //log.info("Site deleted successfully");

            response.setResult(true);
            response.setMessage("Site '" + site.getName() + "' deleted successfully");

        } catch (Exception e) {
            log.error("Error deleting site: ", e);
            response.setResult(false);
            response.setMessage("Error deleting site: " + e.getMessage());
        }

        //log.info("=== END deleteSite ===");
        return response;
    }
}
