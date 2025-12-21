package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.site.AddSiteRequest;
import searchengine.dto.site.AddSiteResponse;
import searchengine.dto.site.DeleteSiteResponse;
import searchengine.models.ConfigSite;
import searchengine.repository.ConfigSiteRepository;
import searchengine.validators.UrlValidator;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteService {
    private final ConfigSiteRepository configSiteRepository;

    /** Добавить новый сайт с полной валидацией */
    public AddSiteResponse addSite(AddSiteRequest request) {
        //log.info("Request - Name: '{}', URL: '{}'", request.getName(), request.getUrl());
        AddSiteResponse response = new AddSiteResponse();

        try {
            // Валидация что это корневой домен
            String validationError = UrlValidator.validateUrl(request.getUrl());
            if (validationError != null) {
                //log.warn("URL validation failed: {}", validationError);
                response.setResult(false);
                response.setMessage(validationError);
                return response;
            }
            //log.info("URL validation passed");

            // Нормализация к стандартному формату корневого домена
            String normalizedUrl = UrlValidator.normalizeUrl(request.getUrl());
            //log.info("Normalized URL: {}", normalizedUrl);

            // Проверка что после нормализации это все еще корневой домен
            if (!UrlValidator.isRootDomain(normalizedUrl)) {
                response.setResult(false);
                response.setMessage("URL must be root domain only");
                return response;
            }

            // Проверка на дубликаты по URL
            if (siteExistsByUrl(request.getUrl())) {
                //log.warn("Site with URL already exists: {}", request.getUrl());
                response.setResult(false);
                response.setMessage("Site with this URL already exists");
                return response;
            }

            // Проверка на дубликаты по имени
            if (siteExistsByName(request.getName())) {
                //log.warn("Site with name already exists: {}", request.getName());
                response.setResult(false);
                response.setMessage("Site with this name already exists");
                return response;
            }
            //log.info("Duplicate check passed");

            // Создание и сохранение сайта
            ConfigSite site = new ConfigSite();
            site.setName(request.getName().trim());
            site.setUrl(normalizedUrl);

            //log.info("Saving site to database...");
            ConfigSite savedSite = saveSite(site);
            //log.info("Site saved successfully with ID: {}", savedSite.getId());

            // Проверка что сайт действительно сохранился
            boolean existsAfterSave = siteExistsByUrl(normalizedUrl);
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

    /** Удалить сайт по имени */
    public DeleteSiteResponse deleteSite(String siteName) {
        //log.info("Deleting site with name: {}", siteName);
        DeleteSiteResponse response = new DeleteSiteResponse();

        try {
            // Находим сайт по имени
            //log.info("Searching for site by name: '{}'", siteName);
            Optional<ConfigSite> siteOptional = getSiteByName(siteName);

            if (siteOptional.isEmpty()) {
                //log.warn("Site not found for name: '{}'", siteName);
                response.setResult(false);
                response.setMessage("Site not found: " + siteName);
                return response;
            }

            ConfigSite site = siteOptional.get();
            deleteSite(site.getId());
            //log.info("Site deleted successfully");

            response.setResult(true);
            response.setMessage("Site '" + site.getName() + "' deleted successfully");
            response.setSiteId(site.getId());

        } catch (Exception e) {
            //log.error("Error deleting site: ", e);
            response.setResult(false);
            response.setMessage("Error deleting site: " + e.getMessage());
        }

        return response;
    }
    /** Получить список всех URL сайтов */
    public List<String> getAllSiteUrls() {
        List<ConfigSite> sites = configSiteRepository.findAll();
        return sites.stream()
                .map(ConfigSite::getUrl)
                .collect(Collectors.toList());
    }

    /** Получить все сайты */
    //public List<ConfigSite> getAllSites() { return configSiteRepository.findAll(); }

    /** Сохранить сайт */
    @Transactional
    public ConfigSite saveSite(ConfigSite site) {
        return configSiteRepository.save(site);
    }

    /** Проверить существование сайта по URL */
    public boolean siteExistsByUrl(String url) {
        boolean exists = configSiteRepository.existsByUrl(url);
        //log.info("Check if site exists by URL '{}': {}", url, exists);
        return exists;
    }

    /** Проверить существование сайта по имени */
    public boolean siteExistsByName(String name) {
        boolean exists = configSiteRepository.existsByName(name);
        //log.info("Check if site exists by name '{}': {}", name, exists);
        return exists;
    }

    /** Удалить сайт по ID (с транзакцией) */
    @Transactional
    public void deleteSite(Integer id) {
        Optional<ConfigSite> siteOptional = configSiteRepository.findById(id);
        if (siteOptional.isPresent()) {
            ConfigSite site = siteOptional.get();
            //log.info("Found site to delete - ID: {}, Name: {}", site.getId(), site.getName());
            configSiteRepository.delete(site);
            //log.info("Site deleted successfully");
        } else {
            //log.warn("Site with ID {} not found for deletion", id);
            throw new RuntimeException("Site with ID " + id + " not found");
        }
    }

    /** Найти сайт по имени */
    public Optional<ConfigSite> getSiteByName(String name) {
        return configSiteRepository.findByName(name);
    }

    /** Найти сайт по ID */
    public Optional<ConfigSite> getSiteById(Integer id) {
        return configSiteRepository.findById(id);
    }

    public Optional<ConfigSite> getSiteByUrl(String url) {
        return configSiteRepository.findByUrl(url);
    }


}
