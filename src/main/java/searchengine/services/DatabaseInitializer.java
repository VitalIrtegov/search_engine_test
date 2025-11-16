package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import searchengine.models.ConfigSite;
import searchengine.repository.ConfigSiteRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseInitializer {
    private final ConfigSiteRepository configSiteRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeConfigSiteData() {
        //log.info("Checking if config_site table needs initialization...");
        // Проверяем таблицу config_site на пустоту
        long siteCount = configSiteRepository.count();

        if (siteCount == 0) {
            //log.info("Initializing default sites in config_site table...");
            List<ConfigSite> defaultSites = List.of(
                    createConfigSite("Лента.ру", "https://www.lenta.ru"),
                    createConfigSite("Skillbox", "https://www.skillbox.ru"),
                    createConfigSite("PlayBack.Ru", "https://www.playback.ru")
            );
            configSiteRepository.saveAll(defaultSites);
            //log.info("Default sites initialized successfully in config_site table");
        } /*else {
            log.info("config_site table already contains {} sites, skipping initialization", siteCount);
        }*/
    }

    private ConfigSite createConfigSite(String name, String url) {
        ConfigSite site = new ConfigSite();
        site.setName(name);
        site.setUrl(url);
        return site;
    }
}
