package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.ConfigSite;
import searchengine.repository.ConfigSiteRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigSiteService {
    private final ConfigSiteRepository configSiteRepository;

    public List<ConfigSite> getAllSites() {
        return configSiteRepository.findAll();
    }

    @Transactional
    public ConfigSite saveSite(ConfigSite site) {
        //log.info("Saving site: {} - {}", site.getName(), site.getUrl());
        return configSiteRepository.save(site);
    }

    public boolean siteExistsByUrl(String url) {
        boolean exists = configSiteRepository.existsByUrl(url);
        log.info("Check if site exists by URL '{}': {}", url, exists);
        return exists;
    }

    public boolean siteExistsByName(String name) {
        boolean exists = configSiteRepository.existsByName(name);
        log.info("Check if site exists by name '{}': {}", name, exists);
        return exists;
    }

    public Optional<ConfigSite> getSiteByUrl(String url) {
        return configSiteRepository.findByUrl(url);
    }

    @Transactional
    public void deleteSite(Integer id) {
        // Сначала находим entity чтобы убедиться что оно managed
        Optional<ConfigSite> siteOptional = configSiteRepository.findById(id);
        if (siteOptional.isPresent()) {
            ConfigSite site = siteOptional.get();
            //log.info("Found site to delete - ID: {}, Name: {}", site.getId(), site.getName());
            configSiteRepository.delete(site); // Удаляем managed entity
            //log.info("Site deleted successfully");
        } else {
            //log.warn("Site with ID {} not found for deletion", id);
            throw new RuntimeException("Site with ID " + id + " not found");
        }
    }

    public Optional<ConfigSite> getSiteById(Integer id) {
        return configSiteRepository.findById(id);
    }

    public Optional<ConfigSite> getSiteByName(String name) {
        return configSiteRepository.findByName(name);
    }
}
