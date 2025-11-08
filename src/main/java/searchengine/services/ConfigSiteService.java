package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.models.ConfigSite;
import searchengine.repository.ConfigSiteRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfigSiteService {
    private final ConfigSiteRepository configSiteRepository;

    public List<ConfigSite> getAllSites() {
        return configSiteRepository.findAll();
    }
}
