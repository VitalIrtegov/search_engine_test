package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.StatisticsSite;
import searchengine.models.ConfigSite;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsService {
    private final SiteService siteService;

    public StatisticsResponse getStatistics() {
        // Получаем все сайты из БД
        List<ConfigSite> sitesFromDb = siteService.getAllSites();

        // Переменные для подсчета общей статистики
        int totalPagesCount = 0;
        int totalLemmasCount = 0;
        boolean isIndexing = false; // Будем определять есть ли индексация

        // Создаем список для детальной статистики по каждому сайту
        List<StatisticsSite> detailedStats = new ArrayList<>();

        // Проходим по каждому сайту из БД и создаем для него статистику
        for (ConfigSite site : sitesFromDb) {
            // Создаем объект для детальной статистики текущего сайта
            StatisticsSite siteStats = new StatisticsSite();

            // Заполняем РЕАЛЬНЫМИ данными из config_site
            siteStats.setName(site.getName());
            siteStats.setUrl(site.getUrl());

            // Заглушки - эти данные будут из других таблиц позже
            if (site.getUrl().equals("https://www.lenta.ru")) {
                siteStats.setPages(82);
                siteStats.setLemmas(59532);
                siteStats.setStatus("INDEXED");
                siteStats.setError("");
            } else if (site.getUrl().equals("https://drewdevault.com")) {
                siteStats.setPages(45);
                siteStats.setLemmas(1234);
                siteStats.setStatus("CRAWLED");
                siteStats.setError("");
            } else if (site.getUrl().equals("https://www.skillbox.ru")) {
                siteStats.setPages(881);
                siteStats.setLemmas(232584);
                siteStats.setStatus("FAILED");
                siteStats.setError("Ошибка индексации: сайт не доступен");
            } else if (site.getUrl().equals("https://sdf.org")) {
                siteStats.setPages(0);
                siteStats.setLemmas(0);
                siteStats.setStatus("CRAWLING");
                siteStats.setError("");
                isIndexing = true; // Есть индексация в процессе
            } else if (site.getUrl().equals("https://www.playback.ru")) {
                siteStats.setPages(151);
                siteStats.setLemmas(7701);
                siteStats.setStatus("INDEXING");
                siteStats.setError("");
                isIndexing = true; // Есть индексация в процессе
            } else {
                // Для остальных сайтов по умолчанию
                siteStats.setPages(0);
                siteStats.setLemmas(0);
                siteStats.setStatus("INDEXED");
                siteStats.setError("");
            }

            // Устанавливаем время статуса (текущее время в миллисекундах)
            siteStats.setStatusTime(System.currentTimeMillis());

            // Суммируем для общей статистики
            totalPagesCount += siteStats.getPages();
            totalLemmasCount += siteStats.getLemmas();

            // Добавляем в список детальной статистики
            detailedStats.add(siteStats);
        }

        // Создаем финальный ответ с новой структурой
        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setTotalSites(sitesFromDb.size());
        response.setTotalPages(totalPagesCount);
        response.setTotalLemmas(totalLemmasCount);
        response.setIndexing(isIndexing);
        response.setSites(detailedStats);

        return response;
    }
}
