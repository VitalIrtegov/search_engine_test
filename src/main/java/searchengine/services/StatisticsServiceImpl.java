package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.models.ConfigSite;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final ConfigSiteService configSiteService;

    @Override
    public StatisticsResponse getStatistics() {
        // Получаем все сайты из БД
        List<ConfigSite> sitesFromDb = configSiteService.getAllSites();

        // Создаем объект для общей статистики
        TotalStatistics totalStats = new TotalStatistics();
        totalStats.setSites(sitesFromDb.size());
        totalStats.setIndexing(true); // заглушка - индексация идет

        // Создаем список для детальной статистики по каждому сайту
        List<DetailedStatisticsItem> detailedStats = new ArrayList<>();

        // Переменные для подсчета общей статистики
        int totalPagesCount = 0;
        int totalLemmasCount = 0;

        // Проходим по каждому сайту из БД и создаем для него статистику
        for (int i = 0; i < sitesFromDb.size(); i++) {
            ConfigSite site = sitesFromDb.get(i);

            // Создаем объект для детальной статистики текущего сайта
            DetailedStatisticsItem siteStats = new DetailedStatisticsItem();

            // Заполняем РЕАЛЬНЫМИ данными из БД
            siteStats.setName(site.getName());  // реальное имя из БД
            siteStats.setUrl(site.getUrl());    // реальный URL из БД

            // заглушки, эти данные будут из других таблиц позже
            if (i == 0) {
                // Для первого сайта (Лента.ру)
                siteStats.setPages(82);
                siteStats.setLemmas(59532);
                siteStats.setStatus("INDEXED");
                siteStats.setError("Ошибка индексации: главная страница сайта не доступна");
            } else if (i == 1) {
                // Для второго сайта (Skillbox)
                siteStats.setPages(881);
                siteStats.setLemmas(232584);
                siteStats.setStatus("FAILED");
                siteStats.setError("Ошибка индексации: сайт не доступен");
            } else {
                // Для третьего и последующих сайтов (PlayBack.Ru и другие)
                siteStats.setPages(151);
                siteStats.setLemmas(7701);
                siteStats.setStatus("INDEXING");
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

        // Заполняем общую статистику
        totalStats.setPages(totalPagesCount);
        totalStats.setLemmas(totalLemmasCount);

        // Создаем объект данных статистики
        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(totalStats);
        statisticsData.setDetailed(detailedStats);

        // Создаем финальный ответ
        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(statisticsData);

        return response;
    }
}
