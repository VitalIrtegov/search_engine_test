package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.*;
import searchengine.repository.SiteRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.ConfigSiteRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final ConfigSiteRepository configSiteRepository;

    private final Map<String, ForkJoinPool> activePools = new ConcurrentHashMap<>();
    private final Map<String, Boolean> stopFlags = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> visitedUrlsMap = new ConcurrentHashMap<>();
    private final Map<String, SiteEntity> activeSites = new ConcurrentHashMap<>();

    @Transactional
    public boolean startIndexing(String siteUrl) {
        // Проверяем, не идет ли уже индексация
        if (activePools.containsKey(siteUrl)) {
            log.warn("Индексация сайта {} уже запущена", siteUrl);
            return false;
        }

        deleteSite(siteUrl); // очистка данных в БД

        ConfigSite configSite = configSiteRepository.findByUrl(siteUrl)
                .orElseThrow(() -> new RuntimeException("Сайт не найден: " + siteUrl));

        // Создаём или получаем SiteEntity
        SiteEntity siteEntity = siteRepository.findByUrl(siteUrl)
                .orElseGet(() -> {
                    SiteEntity newSite = new SiteEntity();
                    newSite.setName(configSite.getName());
                    newSite.setUrl(siteUrl);
                    return newSite;
                });

        siteEntity.setStatus(SiteStatus.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity.setLastError(null);
        siteRepository.save(siteEntity);

        // Сохраняем ссылку на сайт
        activeSites.put(siteUrl, siteEntity);

        log.info("Начинаем индексацию сайта: {}", siteUrl);

        // Инициализируем структуры для отслеживания
        visitedUrlsMap.put(siteUrl, ConcurrentHashMap.newKeySet());
        stopFlags.put(siteUrl, false);

        // Создаем ForkJoinPool для этого сайта
        ForkJoinPool pool = new ForkJoinPool();
        activePools.put(siteUrl, pool);

        // Запускаем индексацию
        pool.execute(() -> {
            try {
                PageIndexer mainTask = new PageIndexer(
                        siteUrl,
                        siteEntity,
                        visitedUrlsMap.get(siteUrl)
                );

                // Выполняем задачу
                pool.invoke(mainTask);

                // После завершения проверяем статус
                if (stopFlags.getOrDefault(siteUrl, false)) {
                    // Пользователь остановил индексацию
                    updateSiteStatus(siteEntity, SiteStatus.FAILED,
                            "Индексация остановлена пользователем");
                    log.info("Индексация сайта {} остановлена пользователем", siteUrl);
                } else {
                    // Автоматическое завершение
                    updateSiteStatus(siteEntity, SiteStatus.INDEXED, null);
                    log.info("Индексация сайта {} завершена. Страниц: {}",
                            siteUrl, visitedUrlsMap.get(siteUrl).size());
                }

            } catch (Exception e) {
                log.error("Критическая ошибка при индексации сайта {}: {}", siteUrl, e.getMessage(), e);
                updateSiteStatus(siteEntity, SiteStatus.FAILED,
                        "Критическая ошибка: " + e.getMessage());
            } finally {
                // Очищаем ресурсы только после обновления статуса
                cleanupResources(siteUrl);
            }
        });

        return true;
    }

    private void updateSiteStatus(SiteEntity siteEntity, SiteStatus status, String error) {
        try {
            // Обновляем сайт из БД (может быть изменен параллельно)
            SiteEntity updatedSite = siteRepository.findById(siteEntity.getId())
                    .orElse(siteEntity);

            updatedSite.setStatus(status);
            updatedSite.setStatusTime(LocalDateTime.now());
            if (error != null) {
                updatedSite.setLastError(error);
            }
            siteRepository.save(updatedSite);

            log.info("Статус сайта {} обновлен на: {}", updatedSite.getUrl(), status);
        } catch (Exception e) {
            log.error("Ошибка при обновлении статуса сайта: {}", e.getMessage());
        }
    }

    public boolean stopIndexing(String siteUrl) {
        if (!activePools.containsKey(siteUrl)) {
            log.warn("Попытка остановить неактивную индексацию: {}", siteUrl);
            return false;
        }

        log.info("Получен запрос на остановку индексации: {}", siteUrl);

        // Устанавливаем флаг остановки
        stopFlags.put(siteUrl, true);

        ForkJoinPool pool = activePools.get(siteUrl);
        if (pool != null) {
            try {
                // Останавливаем пул
                pool.shutdownNow();

                // Ждем завершения
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("Пул не завершился в течение 10 секунд: {}", siteUrl);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Прервано ожидание остановки пула: {}", siteUrl);
            }
        }

        // Немедленно обновляем статус в БД
        SiteEntity siteEntity = activeSites.get(siteUrl);
        if (siteEntity != null) {
            updateSiteStatus(siteEntity, SiteStatus.FAILED,
                    "Индексация остановлена пользователем");
        }

        // Очищаем ресурсы
        cleanupResources(siteUrl);

        log.info("Индексация сайта {} успешно остановлена", siteUrl);
        return true;
    }

    private void cleanupResources(String siteUrl) {
        // Удаляем в правильном порядке
        stopFlags.remove(siteUrl);
        activePools.remove(siteUrl);
        visitedUrlsMap.remove(siteUrl);
        activeSites.remove(siteUrl);

        log.debug("Ресурсы очищены для сайта: {}", siteUrl);
    }

    // Внутренний класс для рекурсивного обхода страниц
    private class PageIndexer extends RecursiveAction {
        private final String url;
        private final SiteEntity siteEntity;
        private final Set<String> visitedUrls;

        public PageIndexer(String url, SiteEntity siteEntity, Set<String> visitedUrls) {
            this.url = url;
            this.siteEntity = siteEntity;
            this.visitedUrls = visitedUrls;
        }

        @Override
        protected void compute() {
            if (stopFlags.getOrDefault(siteEntity.getUrl(), false)) return;

            String normalizedUrl = normalizeUrl(url);
            if (!visitedUrls.add(normalizedUrl)) return;

            if (!isValidUrlForDownload(url)) {
                return; // Пропускаем изображения
            }

            //log.info("Обрабатываем URL: {}", url);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                Connection.Response response = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (compatible; SearchEngineBot/1.0)")
                        .referrer("http://www.google.com")
                        .timeout(5000)
                        .ignoreHttpErrors(true)
                        .execute();

                int statusCode = response.statusCode();
                String contentType = response.contentType();
                String body = response.body(); // Получаем тело ответа

                // ВСЕГДА сохраняем контент, независимо от типа
                savePageToDatabase(url, statusCode, body, siteEntity);

                if (statusCode == 200 && contentType != null && contentType.contains("text/html")) {
                    try {
                        Document doc = response.parse();
                        Elements links = doc.select("a[href]");
                        //log.info("На странице {} найдено {} ссылок", url, links.size());

                        List<PageIndexer> subtasks = new ArrayList<>();

                        for (Element link : links) {
                            String href = link.absUrl("href");
                            String normalizedHref = normalizeUrl(href);

                            // Используйте фильтрацию!
                            if (isValidForIndexing(normalizedHref, siteEntity.getUrl())) {
                                if (!visitedUrls.contains(normalizedHref)) {
                                    subtasks.add(new PageIndexer(normalizedHref, siteEntity, visitedUrls));
                                }
                            }
                        }

                        //log.info("Создано {} подзадач для страницы {}", subtasks.size(), url);

                        if (!subtasks.isEmpty()) {
                            invokeAll(subtasks);
                        }

                    } catch (Exception e) {
                        log.warn("Ошибка парсинга {}: {}", url, e.getMessage());
                    }
                }

            } catch (IOException e) {
                // Сохраняем страницу с ошибкой
                savePageToDatabase(url, 0, "", siteEntity);

                // Фильтруем логи content type ошибок
                if (!e.getMessage().contains("Unhandled content type")) {
                    log.warn("Ошибка загрузки {}: {}", url, e.getMessage());
                }
            }
        }

        private boolean isHtmlUrl(String url) {
            // Только явно не-текстовые форматы отсеиваем
            String lowerUrl = url.toLowerCase();

            // Отсеиваем ТОЛЬКО очевидные не-HTML
            return !lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|ico|svg|mp4|mp3|avi|mov|wmv|flv|pdf|zip|rar|7z|tar|gz|exe|dmg|pkg|apk|css|js|woff|woff2|ttf|eot|otf)$");
            // Оставляем .html, .php, .asp и другие, которые МОГУТ быть HTML

            // Проверяем расширения файлов
            /*String lowerUrl = url.toLowerCase();
            return !lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|ico|svg|mp4|mp3|avi|mov|wmv|flv|pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|7z|tar|gz|exe|dmg|pkg|apk|css|js|woff|woff2|ttf|eot)$");*/
        }

        private boolean isAllowedContentType(String contentType) {
            if (contentType == null) return false;

            String lowerType = contentType.toLowerCase();
            return lowerType.contains("text/html") ||
                    lowerType.contains("application/xhtml+xml") ||
                    lowerType.contains("application/xml") ||
                    lowerType.contains("text/plain");
        }

        private boolean isValidForIndexing(String url, String baseUrl) {
            if (url == null || url.isEmpty()) return false;

            if (!url.startsWith("http://") && !url.startsWith("https://")) return false;

            String lowerUrl = url.toLowerCase();

            // Фильтруем JSON API endpoints
            if (lowerUrl.matches(".*/(posts|comments|users|photos|albums|todos)(/.*)?$")) {
                return false;
            }

            if (lowerUrl.startsWith("mailto:") ||
                    lowerUrl.startsWith("tel:") ||
                    lowerUrl.startsWith("javascript:")) {
                return false;
            }

            // Проверяем тот же домен
            if (!isSameDomain(url, baseUrl)) {
                return false;
            }

            // Только явные изображения отсеиваем
            return !lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|ico|svg)$");
        }

        private String normalizeUrl(String url) {
            try {
                URI uri = new URI(url);
                String normalized = uri.normalize().toString();

                // Удаляем якорь
                int anchorIndex = normalized.indexOf('#');
                if (anchorIndex != -1) {
                    normalized = normalized.substring(0, anchorIndex);
                }

                // Удаляем trailing slash для консистентности
                if (normalized.endsWith("/") && normalized.length() > 1) {
                    normalized = normalized.substring(0, normalized.length() - 1);
                }

                return normalized;
            } catch (URISyntaxException e) {
                return url;
            }
        }

        private boolean isSameDomain(String url1, String url2) {
            try {
                URI uri1 = new URI(url1);
                URI uri2 = new URI(url2);

                String host1 = uri1.getHost();
                String host2 = uri2.getHost();

                if (host1 == null || host2 == null) {
                    return false;
                }

                // Убираем www для сравнения
                host1 = host1.replaceFirst("^www\\.", "");
                host2 = host2.replaceFirst("^www\\.", "");

                return host1.equals(host2);
            } catch (URISyntaxException e) {
                return false;
            }
        }

        private void savePageToDatabase(String url, int statusCode, String content, SiteEntity siteEntity) {
            try {
                String path = extractPathFromUrl(url, siteEntity.getUrl());

                log.info("Сохранение страницы: {} (код: {}, длина: {})",
                        path, statusCode, content.length());

                // Проверяем, не существует ли уже такая страница
                Optional<PageEntity> existingPage = pageRepository.findBySiteAndPath(siteEntity, path);

                PageEntity pageEntity = existingPage.orElse(new PageEntity());
                pageEntity.setSite(siteEntity);
                pageEntity.setPath(path);
                pageEntity.setCode(statusCode);
                pageEntity.setContent(content);

                pageRepository.save(pageEntity);
            } catch (Exception e) {
                log.error("Ошибка при сохранении страницы {}: {}", url, e.getMessage());
            }
        }

        private String extractPathFromUrl(String fullUrl, String baseUrl) {
            try {
                URI baseUri = new URI(baseUrl);
                URI fullUri = new URI(fullUrl);

                String path = fullUri.getPath();
                if (path == null || path.isEmpty()) {
                    path = "/";
                }

                String query = fullUri.getQuery();
                if (query != null && !query.isEmpty()) {
                    path = path + "?" + query;
                }

                String fragment = fullUri.getFragment();
                if (fragment != null && !fragment.isEmpty()) {
                    path = path + "#" + fragment;
                }

                return path;
            } catch (URISyntaxException e) {
                return "/error";
            }
        }
    }

    public int getIndexedPagesCount(String siteUrl) {
        Set<String> visited = visitedUrlsMap.get(siteUrl);
        return visited != null ? visited.size() : 0;
    }

    public SiteStatus getSiteStatus(String siteUrl) {
        return siteRepository.findByUrl(siteUrl)
                .map(SiteEntity::getStatus)
                .orElse(SiteStatus.FAILED);
    }

    private boolean isValidUrlForDownload(String url) {
        String lowerUrl = url.toLowerCase();
        // Фильтруем явные не-HTML ссылки
        return !lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|ico|svg|mp4|mp3|avi|mov|wmv|flv|pdf|zip|rar)$");
        // jpg|jpeg|png|gif|webp|svg|ico|bmp|tiff|pdf|zip|rar|7z|tar|gz|doc|docx|xls|xlsx|ppt|pptx|mp3|mp4|avi|mov|wmv|flv|css|js
    }

    @Transactional
    private void deleteSite(String siteUrl) {
        pageRepository.deleteBySiteUrl(siteUrl);
        siteRepository.deleteByUrl(siteUrl);
        log.info("Удалены данные сайта: {}", siteUrl);
    }
}
