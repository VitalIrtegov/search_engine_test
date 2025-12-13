package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.ConfigIndexing;
import searchengine.models.*;
import searchengine.repository.SiteRepository;
import searchengine.repository.PageRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingTaskService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final ConfigIndexing configIndexing;

    /**
     * ЗАПУСК задачи индексации для сайта
     */
    public CompletableFuture<Boolean> startIndexingTask(SiteEntity site) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🚀 Начало индексации сайта: {}", site.getUrl());

            try {
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

                IndexingTask mainTask = new IndexingTask(site, site.getUrl(), visitedUrls);
                Boolean result = forkJoinPool.invoke(mainTask);

                log.info("🏁 Индексация завершена для {}: {}", site.getUrl(), result);
                return result;

            } catch (Exception e) {
                log.error("💥 Ошибка индексации для {}: {}", site.getUrl(), e.getMessage());
                return false;
            }
        });
    }

    /**
     * ОСТАНОВКА задачи индексации
     */
    public void stopIndexingTask(IndexingTask task) {
        if (task != null) {
            task.stop();
        }
    }

    /**
     * ВНУТРЕННИЙ КЛАСС - задача индексации
     */
    private class IndexingTask extends RecursiveTask<Boolean> {
        private final SiteEntity site;
        private final String url;
        private final Set<String> visitedUrls;
        private volatile boolean isStopped = false;

        public IndexingTask(SiteEntity site, String url, Set<String> visitedUrls) {
            this.site = site;
            this.url = url;
            this.visitedUrls = visitedUrls;
        }

        @Override
        protected Boolean compute() {
            // Проверка остановки и дубликатов
            if (isStopped) {
                log.debug("⏹️ Остановлено: {}", url);
                return false;
            }

            if (visitedUrls.contains(url)) {
                log.debug("♻️ Уже посещено: {}", url);
                return false;
            }

            // Добавляем в посещённые
            visitedUrls.add(url);
            log.debug("🔍 Обработка: {}", url);

            try {
                // Пауза между запросами
                int delay = configIndexing.getDelay().getMin() +
                        new Random().nextInt(configIndexing.getDelay().getMax() -
                                configIndexing.getDelay().getMin());
                Thread.sleep(delay);

                // Загрузка страницы
                Document doc = Jsoup.connect(url)
                        .userAgent(configIndexing.getUserAgent())
                        .referrer(configIndexing.getReferrer())
                        .timeout(configIndexing.getTimeout())
                        .get();

                // СОХРАНЕНИЕ страницы в БД
                savePageToDatabase(doc);

                // Обновление времени статуса сайта
                updateSiteStatusTime();

                // Поиск ссылок для дальнейшей индексации
                List<IndexingTask> subtasks = findAndCreateSubtasks(doc);

                // Запуск подзадач
                invokeAll(subtasks);

                log.debug("✅ Завершено: {}", url);
                return true;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("⏸️ Прервано: {}", url);
                return false;
            } catch (Exception e) {
                log.warn("⚠️ Ошибка {}: {}", url, e.getMessage());
                saveErrorPage(e.getMessage());
                return false;
            }
        }

        /**
         * Сохранение страницы в БД
         */
        private void savePageToDatabase(Document doc) {
            try {
                PageEntity page = new PageEntity();
                page.setSite(site);
                page.setPath(extractPath(url));
                page.setContentHtml(doc.html());
                page.setCode(200);

                pageRepository.save(page);

                log.debug("💾 Сохранено: {}", url);

            } catch (Exception e) {
                log.error("❌ Ошибка сохранения {}: {}", url, e.getMessage());
            }
        }

        /**
         * Сохранение страницы с ошибкой
         */
        private void saveErrorPage(String error) {
            try {
                PageEntity page = new PageEntity();
                page.setSite(site);
                page.setPath(extractPath(url));
                page.setContentHtml("");
                page.setCode(500);

                pageRepository.save(page);

                log.debug("💾 Сохранено с ошибкой: {}", url);

            } catch (Exception e) {
                log.error("❌ Ошибка сохранения ошибки {}: {}", url, e.getMessage());
            }
        }

        /**
         * Поиск ссылок и создание подзадач
         */
        private List<IndexingTask> findAndCreateSubtasks(Document doc) {
            List<IndexingTask> subtasks = new ArrayList<>();

            Elements links = doc.select("a[href]");
            String baseDomain = extractDomain(site.getUrl());

            for (Element link : links) {
                if (isStopped) break;

                String href = link.attr("abs:href");

                if (isValidLink(href, baseDomain) && !visitedUrls.contains(href)) {
                    IndexingTask subtask = new IndexingTask(site, href, visitedUrls);
                    subtasks.add(subtask);
                }
            }

            log.debug("🔗 Найдено {} ссылок на: {}", subtasks.size(), url);
            return subtasks;
        }

        /**
         * Обновление времени статуса сайта
         */
        private void updateSiteStatusTime() {
            try {
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            } catch (Exception e) {
                log.warn("⚠️ Не удалось обновить время статуса: {}", e.getMessage());
            }
        }

        /**
         * Остановка задачи
         */
        public void stop() {
            this.isStopped = true;
        }

        /**
         * Извлечение пути из URL
         */
        private String extractPath(String url) {
            try {
                java.net.URL urlObj = new java.net.URL(url);
                String path = urlObj.getPath();
                String query = urlObj.getQuery();
                return path + (query != null ? "?" + query : "");
            } catch (Exception e) {
                return url;
            }
        }

        /**
         * Извлечение домена из URL
         */
        private String extractDomain(String url) {
            try {
                return new java.net.URL(url).getHost();
            } catch (Exception e) {
                return "";
            }
        }

        /**
         * Проверка валидности ссылки
         */
        private boolean isValidLink(String href, String baseDomain) {
            if (href == null || href.isEmpty() ||
                    href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:")) {
                return false;
            }

            try {
                java.net.URL url = new java.net.URL(href);
                String protocol = url.getProtocol();
                String host = url.getHost();

                return ("http".equals(protocol) || "https".equals(protocol)) &&
                        host != null && host.equals(baseDomain);
            } catch (Exception e) {
                return false;
            }
        }
    }

}
