package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.StatisticsSite;
import searchengine.models.*;
import searchengine.repository.ConfigSiteRepository;
import searchengine.repository.SiteRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.LemmaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatisticsService {
    private final ConfigSiteRepository configSiteRepository;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    public StatisticsResponse getStatistics() {
        // Получаем все сайты из БД
        List<ConfigSite> sitesDb = configSiteRepository.findAll();

        // Переменные для подсчета общей статистики
        int totalPagesCount = 0;
        int totalLemmasCount = 0;
        boolean isIndexing = false; // определить есть ли индексация

        // Создаем список для детальной статистики по каждому сайту
        List<StatisticsSite> detailedStats = new ArrayList<>();

        // Проходим по каждому сайту из БД и создаем для него статистику
        for (ConfigSite site : sitesDb) {
            // Создаем объект для детальной статистики текущего сайта
            StatisticsSite siteStats = new StatisticsSite();

            // реальные данные из config_site
            siteStats.setName(site.getName());
            siteStats.setUrl(site.getUrl());

            // Ищем соответствующий SiteEntity (может не быть)
            Optional<SiteEntity> siteEntityOpt = siteRepository.findByUrl(site.getUrl());

            if (siteEntityOpt.isPresent()) {
                SiteEntity siteEntity = siteEntityOpt.get();
                // Данные из SiteEntity (если есть)
                siteStats.setStatus(siteEntity.getStatus().toString());
                siteStats.setError(siteEntity.getLastError());
                siteStats.setStatusTime(siteEntity.getStatusTime() != null
                        ? siteEntity.getStatusTime().toEpochSecond(java.time.ZoneOffset.UTC) * 1000
                        : 0);

                // Количество страниц
                long pagesCount = pageRepository.countBySite(siteEntity);
                siteStats.setPages((int) pagesCount);
                totalPagesCount += pagesCount;

                // Количество лемм
                long lemmasCount = lemmaRepository.countBySite(siteEntity);
                siteStats.setLemmas((int) lemmasCount);
                totalLemmasCount += lemmasCount;

                // Проверка индексации
                if (siteEntity.getStatus() == SiteStatus.INDEXING) {
                    isIndexing = true;
                }

            } else {
                // Сайт в конфигурации, но не индексировался
                siteStats.setStatus("NOT_INDEXED");
                siteStats.setError("");
                siteStats.setStatusTime(0);
                siteStats.setPages(0);
                siteStats.setLemmas(0);
            }

            detailedStats.add(siteStats);
        }

        // Создаем финальный ответ с новой структурой
        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setTotalSites(sitesDb.size());
        response.setTotalPages(totalPagesCount);
        response.setTotalLemmas(totalLemmasCount);
        response.setIndexing(isIndexing);
        response.setSites(detailedStats);

        return response;
    }
}
