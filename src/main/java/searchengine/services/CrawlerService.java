package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.ConfigCrawler;
import searchengine.models.ConfigSite;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatus;
import searchengine.models.PageEntity;
import searchengine.repository.SiteRepository;
import searchengine.repository.PageRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SiteService siteService;
    private final ConfigCrawler configCrawler;

    // Активные обходы для управления
    private final Map<String, CrawlTask> activeCrawls = new ConcurrentHashMap<>();

    /**
     * Запуск обхода одного сайта или всех сайтов
     */
    public boolean startCrawling(String siteName) {
        try {
            // Проверяем не запущен ли уже обход
            if (activeCrawls.containsKey(siteName)) {
                log.warn("Crawling already running for site: {}", siteName);
                return false;
            }

            // Находим сайт по имени
            Optional<ConfigSite> siteOpt = siteService.getSiteByName(siteName);
            if (siteOpt.isEmpty()) {
                log.error("Site not found: {}", siteName);
                return false;
            }

            // Запускаем обход
            ConfigSite configSite = siteOpt.get();
            CrawlTask crawlTask = new CrawlTask(configSite);
            activeCrawls.put(siteName, crawlTask);

            // Запускаем в отдельном пуле ForkJoin
            ForkJoinPool pool = new ForkJoinPool();
            pool.execute(crawlTask);

            log.info("Started crawling for site: {}", siteName);
            return true;

        } catch (Exception e) {
            log.error("Failed to start crawling for site: {}", siteName, e);
            return false;
        }
    }

    /**
     * Запуск обхода всех сайтов
     */
    private boolean startCrawlingAll() {
        try {
            List<ConfigSite> allSites = siteService.getAllSites();
            log.info("Starting crawling for {} sites", allSites.size());

            int startedCount = 0;
            for (ConfigSite site : allSites) {
                if (startCrawling(site.getName())) {
                    startedCount++;
                }
            }

            log.info("Started crawling for {}/{} sites", startedCount, allSites.size());
            return startedCount > 0;

        } catch (Exception e) {
            log.error("Failed to start crawling for all sites", e);
            return false;
        }
    }

    /** Остановка обхода */
    public boolean stopCrawling(String siteName) {
        try {
            if ("all".equals(siteName)) {
                stopAllCrawlings();
                return true;
            }

            CrawlTask crawlTask = activeCrawls.get(siteName);
            if (crawlTask != null) {
                crawlTask.stop("Обход остановлен пользователем");
                activeCrawls.remove(siteName);
                log.info("Stopped crawling for site: {}", siteName);
                return true;
            }
            return false;

        } catch (Exception e) {
            log.error("Failed to stop crawling for site: {}", siteName, e);
            return false;
        }
    }

    /**
     * Остановка всех обходов
     */
    public void stopAllCrawlings() {
        log.info("Stopping all crawlings");

        // Создаем копию ключей чтобы избежать ConcurrentModificationException
        List<String> siteNames = new ArrayList<>(activeCrawls.keySet());
        for (String siteName : siteNames) {
            stopCrawling(siteName);
        }

        log.info("All crawlings stopped");
    }

    /**
     * Проверка активен ли обход для сайта
     */
    public boolean isCrawlingActive(String siteName) {
        return activeCrawls.containsKey(siteName);
    }

    /**
     * Получение списка активных обходов
     */
    public Set<String> getActiveCrawlings() {
        return new HashSet<>(activeCrawls.keySet());
    }

    /**
     * Внутренний класс задачи обхода с использованием Fork-Join
     */
    private class CrawlTask extends RecursiveTask<Boolean> {
        private final ConfigSite configSite;
        private final AtomicBoolean isStopped = new AtomicBoolean(false);
        private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
        private final Set<String> urlBatch = ConcurrentHashMap.newKeySet();
        private final ForkJoinPool forkJoinPool;
        private SiteEntity siteEntity;
        private Random random = new Random();

        public CrawlTask(ConfigSite configSite) {
            this.configSite = configSite;
            this.forkJoinPool = new ForkJoinPool();
        }

        @Override
        protected Boolean compute() {
            try {
                initializeCrawling();
                return processCrawling();

            } catch (Exception e) {
                log.error("Crawling error for site: {}", configSite.getUrl(), e);
                handleCrawlingError("Ошибка обхода: " + e.getMessage());
                return false;
            } finally {
                cleanup();
            }
        }

        /**
         * Инициализация обхода
         */
        private void initializeCrawling() {
            // Удаляем старые данные страниц для этого сайта
            pageRepository.deleteBySiteUrl(configSite.getUrl());

            // Ищем существующую запись или создаем новую
            siteEntity = siteRepository.findByUrl(configSite.getUrl())
                    .orElse(new SiteEntity());

            // Заполняем/обновляем данные
            siteEntity.setUrl(configSite.getUrl());
            siteEntity.setName(configSite.getName());
            siteEntity.setStatus(SiteStatus.CRAWLING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setLastError(null);

            siteRepository.save(siteEntity);

            log.info("Initialized crawling for site: {}", configSite.getUrl());
        }

        /**
         * Основной процесс обхода
         */
        private Boolean processCrawling() {
            List<PageProcessor> tasks = new ArrayList<>();
            String baseDomain = extractDomain(configSite.getUrl());

            // Начинаем с главной страницы
            tasks.add(new PageProcessor(configSite.getUrl(), baseDomain, 0));

            // Выполняем задачи
            for (PageProcessor task : tasks) {
                if (isStopped.get()) {
                    break;
                }
                task.fork();
            }

            // Ждем завершения
            for (PageProcessor task : tasks) {
                if (isStopped.get()) {
                    break;
                }
                task.join();
            }

            // Сохраняем последний батч
            if (!urlBatch.isEmpty()) {
                saveUrlBatch();
            }

            // Завершаем обход если не остановлен
            if (!isStopped.get()) {
                completeCrawling();
                return true;
            }

            return false;
        }

        /**
         * Класс для обработки отдельной страницы
         */
        private class PageProcessor extends RecursiveAction {
            private final String url;
            private final String baseDomain;
            private final int depth;

            public PageProcessor(String url, String baseDomain, int depth) {
                this.url = url;
                this.baseDomain = baseDomain;
                this.depth = depth;
            }

            @Override
            protected void compute() {
                if (isStopped.get() || visitedUrls.contains(url)) {
                    return;
                }

                // Помечаем как посещенную
                visitedUrls.add(url);

                try {
                    // Случайная задержка из конфига
                    int delay = configCrawler.getDelay().getMin() +
                            random.nextInt(configCrawler.getDelay().getMax() -
                                    configCrawler.getDelay().getMin());
                    Thread.sleep(delay);

                    // Загружаем страницу с настройками из конфига
                    Document doc = Jsoup.connect(url)
                            .userAgent(configCrawler.getUserAgent())
                            .referrer(configCrawler.getReferrer())
                            .timeout(configCrawler.getTimeout())
                            .get();

                    // Сохраняем страницу в БД
                    savePage(url, doc);

                    // Добавляем в батч для контроля лимита
                    synchronized (urlBatch) {
                        urlBatch.add(url);

                        // Сохраняем батч при достижении лимита 1000
                        if (urlBatch.size() >= 1000) {
                            saveUrlBatch();
                        }
                    }

                    // Обновляем время статуса
                    updateStatusTime();

                    // Извлекаем и обрабатываем ссылки
                    Elements links = doc.select("a[href]");
                    List<PageProcessor> subTasks = new ArrayList<>();

                    for (Element link : links) {
                        if (isStopped.get()) {
                            break;
                        }

                        String href = link.attr("abs:href");

                        // Проверяем валидность ссылки и лимит в 1000 страниц
                        if (isValidLink(href, baseDomain) &&
                                !visitedUrls.contains(href) &&
                                visitedUrls.size() < 1000) {

                            subTasks.add(new PageProcessor(href, baseDomain, depth + 1));
                        }
                    }

                    // Запускаем подзадачи
                    invokeAll(subTasks);

                } catch (IOException e) {
                    log.warn("Failed to load page: {}", url, e);
                    saveErrorPage(url, e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Crawling interrupted for: {}", url);
                } catch (Exception e) {
                    log.warn("Error processing page: {}", url, e);
                }
            }
        }

        /**
         * Сохранение страницы в базу
         */
        private void savePage(String url, Document doc) {
            try {
                PageEntity page = new PageEntity();
                page.setSite(siteEntity);
                page.setPath(extractPath(url));
                page.setCode(200);
                page.setContent(doc.html());
                pageRepository.save(page);

                log.debug("Saved page: {}", url);
            } catch (Exception e) {
                log.error("Failed to save page: {}", url, e);
            }
        }

        /**
         * Сохранение страницы с ошибкой
         */
        private void saveErrorPage(String url, String error) {
            try {
                PageEntity page = new PageEntity();
                page.setSite(siteEntity);
                page.setPath(extractPath(url));
                page.setCode(404);
                page.setContent("Error: " + error);
                pageRepository.save(page);
            } catch (Exception e) {
                log.error("Failed to save error page: {}", url, e);
            }
        }

        /**
         * Сохранение батча URL с паузой
         */
        private synchronized void saveUrlBatch() {
            if (urlBatch.isEmpty()) return;

            try {
                log.info("Saving batch of {} URLs for site: {}", urlBatch.size(), configSite.getUrl());

                // Пауза 5 секунд при сохранении батча
                Thread.sleep(5000);

                // Здесь можно добавить дополнительную логику обработки батча
                // Например, массовое сохранение в БД или проверку дубликатов

                urlBatch.clear();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Завершение обхода
         */
        private void completeCrawling() {
            siteEntity.setStatus(SiteStatus.CRAWLED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
            log.info("Crawling completed for site: {}. Pages visited: {}",
                    configSite.getUrl(), visitedUrls.size());
        }

        /**
         * Обработка ошибки обхода
         */
        private void handleCrawlingError(String error) {
            siteEntity.setStatus(SiteStatus.FAILED);
            siteEntity.setLastError(error);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }

        /**
         * Обновление времени статуса
         */
        private void updateStatusTime() {
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }

        /**
         * Остановка обхода
         */
        public void stop(String reason) {
            isStopped.set(true);
            forkJoinPool.shutdownNow();
            handleCrawlingError(reason);

            // Сохраняем текущий батч при остановке
            if (!urlBatch.isEmpty()) {
                saveUrlBatch();
            }
        }

        private void cleanup() {
            activeCrawls.remove(configSite.getName());
            try {
                forkJoinPool.shutdown();
                if (!forkJoinPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    forkJoinPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                forkJoinPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Вспомогательные методы
        private String extractDomain(String url) {
            try {
                return new java.net.URL(url).getHost();
            } catch (Exception e) {
                log.warn("Failed to extract domain from URL: {}", url, e);
                return "";
            }
        }

        private String extractPath(String url) {
            try {
                java.net.URL urlObj = new java.net.URL(url);
                String path = urlObj.getPath();
                String query = urlObj.getQuery();
                return path + (query != null ? "?" + query : "");
            } catch (Exception e) {
                log.warn("Failed to extract path from URL: {}", url, e);
                return url;
            }
        }

        private boolean isValidLink(String href, String baseDomain) {
            if (href == null || href.isEmpty() || href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:")) {
                return false;
            }

            try {
                java.net.URL url = new java.net.URL(href);
                String protocol = url.getProtocol();
                String host = url.getHost();

                // Проверяем протокол и домен
                return ("http".equals(protocol) || "https".equals(protocol)) &&
                        host != null && host.equals(baseDomain);

            } catch (Exception e) {
                return false;
            }
        }

    }
}
