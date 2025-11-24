package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.models.SiteEntity;
import searchengine.repository.SiteRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {
    private final SiteRepository siteRepository;
    private final IndexingTaskService indexingTaskService;

    private final ConcurrentMap<String, CompletableFuture<Boolean>> activeIndexings = new ConcurrentHashMap<>();

    public boolean startIndexing(String siteName) {
        try {
            if (activeIndexings.containsKey(siteName)) {
                log.warn("Indexing already running for site: {}", siteName);
                return false;
            }

            Optional<SiteEntity> siteOpt = siteRepository.findByName(siteName);
            if (siteOpt.isEmpty()) {
                log.error("Site not found: {}", siteName);
                return false;
            }

            SiteEntity site = siteOpt.get();
            CompletableFuture<Boolean> task = indexingTaskService.startIndexingTask(site);

            task.whenComplete((result, error) -> {
                activeIndexings.remove(siteName);
                if (error != null) {
                    log.error("Indexing failed for site: {}", siteName, error);
                }
            });

            activeIndexings.put(siteName, task);
            log.info("Started indexing for site: {}", siteName);
            return true;

        } catch (Exception e) {
            log.error("Failed to start indexing for site: {}", siteName, e);
            return false;
        }
    }

    public boolean startIndexingAll() {
        try {
            List<SiteEntity> allSites = siteRepository.findAll();
            log.info("Starting indexing for {} sites", allSites.size());

            int startedCount = 0;
            for (SiteEntity site : allSites) {
                if (startIndexing(site.getName())) {
                    startedCount++;
                }
            }

            log.info("Started indexing for {}/{} sites", startedCount, allSites.size());
            return startedCount > 0;

        } catch (Exception e) {
            log.error("Failed to start indexing for all sites", e);
            return false;
        }
    }

    public boolean stopIndexing() {
        try {
            log.info("Stopping all indexings");

            for (CompletableFuture<Boolean> task : activeIndexings.values()) {
                task.cancel(true);
            }
            activeIndexings.clear();

            log.info("All indexings stopped");
            return true;

        } catch (Exception e) {
            log.error("Failed to stop indexing", e);
            return false;
        }
    }

    public boolean isIndexingActive(String siteName) {
        return activeIndexings.containsKey(siteName);
    }
}
