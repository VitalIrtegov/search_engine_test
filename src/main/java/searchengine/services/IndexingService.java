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
import org.springframework.transaction.support.TransactionTemplate;
import searchengine.config.ConfigIndexing;
import searchengine.models.*;
import searchengine.repository.*;

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
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaService lemmaService;
    private final ConfigIndexing configIndexing;
    private final Random random = new Random();
    private final TransactionTemplate transactionTemplate;

    private final Map<String, ForkJoinPool> activePools = new ConcurrentHashMap<>();
    private final Map<String, Boolean> stopFlags = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> visitedUrlsMap = new ConcurrentHashMap<>();
    private final Map<String, SiteEntity> activeSites = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> pageLemmasCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> siteLemmasCache = new ConcurrentHashMap<>();
    private final Map<Integer, Float> idfCache = new ConcurrentHashMap<>();

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
        siteLemmasCache.put(siteUrl, new ConcurrentHashMap<>());

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
                    completeSiteIndexing(siteUrl, siteEntity);
                    updateSiteStatus(siteEntity, SiteStatus.INDEXED, null);

                    log.info("Индексация сайта {} завершена. Страниц: {}",
                            siteUrl,
                            Optional.ofNullable(visitedUrlsMap.get(siteUrl))
                                    .map(Set::size)
                                    .orElse(0)
                    );
                    /*log.info("Индексация сайта {} завершена. Страниц: {}",
                            siteUrl, visitedUrlsMap.get(siteUrl).size());*/
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

    private void completeSiteIndexing(String siteUrl, SiteEntity siteEntity) {
        try {
            processLemmasForSite(siteUrl, siteEntity);

            siteLemmasCache.remove(siteUrl);
            //pageLemmasCache.keySet().removeIf(key -> key.endsWith("_" + siteUrl));
            pageLemmasCache.keySet().removeIf(key -> key.startsWith(siteUrl));

            log.info("Обработка лемм для сайта {} завершена", siteUrl);

        } catch (Exception e) {
            log.error("Ошибка при завершении обработки лемм сайта {}: {}", siteUrl, e.getMessage());
        }
    }

    private void processLemmasForSite(String siteUrl, SiteEntity siteEntity) {
        try {
            log.info("Начинаем обработку лемм для сайта: {}", siteUrl);

            Map<String, Integer> siteLemmas = siteLemmasCache.get(siteUrl);
            if (siteLemmas == null || siteLemmas.isEmpty()) {
                log.info("Нет лемм для обработки сайта: {}", siteUrl);
                return;
            }

            // Получаем общее количество страниц
            List<PageEntity> pages = pageRepository.findBySite(siteEntity);
            int totalPages = pages.size();
            log.info("Всего страниц для расчета frequency: {}", totalPages);

            // Сохраняем леммы с frequency (количество страниц с этой леммой)
            for (Map.Entry<String, Integer> entry : siteLemmas.entrySet()) {
                String lemmaText = entry.getKey();
                int pageCount = entry.getValue(); // сколько страниц содержит эту лемму

                // Исправляем, если больше totalPages
                if (pageCount > totalPages) {
                    log.warn("Исправляем frequency леммы '{}': было {}, станет {}",
                            lemmaText, pageCount, totalPages);
                    pageCount = totalPages;
                }

                LemmaEntity lemmaEntity = lemmaRepository
                        .findBySiteAndLemma(siteEntity, lemmaText)
                        .orElseGet(() -> {
                            LemmaEntity newLemma = new LemmaEntity();
                            newLemma.setSite(siteEntity);
                            newLemma.setLemma(lemmaText);
                            newLemma.setFrequency(0);
                            return newLemma;
                        });

                // frequency = количество страниц, где встречается эта лемма
                lemmaEntity.setFrequency(pageCount);
                lemmaRepository.save(lemmaEntity);
            }

            log.info("Обработано {} уникальных лемм для сайта: {}", siteLemmas.size(), siteUrl);

            // Теперь рассчитываем TF-IDF с правильными frequency
            processRankCountForSite(siteUrl, siteEntity);

        } catch (Exception e) {
            log.error("Ошибка при обработке лемм для сайта {}: {}", siteUrl, e.getMessage());
        }
    }

    private void processRankCountForSite(String siteUrl, SiteEntity siteEntity) {
        try {
            List<PageEntity> pages = pageRepository.findBySite(siteEntity);
            int totalPages = pages.size();

            if (totalPages == 0) {
                log.info("Нет страниц для расчета rank у сайта: {}", siteUrl);
                return;
            }

            log.info("Расчет TF-IDF для сайта {}: {} страниц", siteUrl, totalPages);

            // Батчинг для сохранения
            List<IndexEntity> batch = new ArrayList<>(1000);

            for (PageEntity page : pages) {
                //String pageKey = page.getId() + "_" + siteUrl;
                String pageKey = siteUrl + page.getPath();
                Map<String, Integer> pageLemmas = pageLemmasCache.get(pageKey);

                if (pageLemmas != null && !pageLemmas.isEmpty()) {
                    for (Map.Entry<String, Integer> entry : pageLemmas.entrySet()) {
                        String lemmaText = entry.getKey();
                        int tf = entry.getValue(); // Term Frequency

                        // Находим лемму в БД
                        LemmaEntity lemmaEntity = lemmaRepository.findBySiteAndLemma(siteEntity, lemmaText)
                                .orElse(null);

                        if (lemmaEntity != null && lemmaEntity.getFrequency() > 0) {
                            // Оптимизация: пропустить стоп-слова и редкие леммы
                            if (lemmaEntity.getFrequency() >= totalPages * 0.8) {
                                continue; // Слишком частая лемма (стоп-слово)
                            }

                            // Быстрый расчет с кэшированием
                            float idf = calculateIdf(totalPages, lemmaEntity.getFrequency());
                            float rank = tf * idf;

                            // Создаем индекс
                            IndexEntity indexEntity = indexRepository
                                    .findByPageAndLemma(page, lemmaEntity)
                                    .orElseGet(() -> {
                                        IndexEntity newIndex = new IndexEntity();
                                        newIndex.setPage(page);
                                        newIndex.setLemma(lemmaEntity);
                                        newIndex.setRank_count(0f);
                                        return newIndex;
                                    });

                            indexEntity.setRank_count(rank);
                            batch.add(indexEntity);

                            // Сохраняем батчем
                            if (batch.size() >= 1000) {
                                indexRepository.saveAll(batch);
                                batch.clear();
                                log.debug("Сохранен батч из 1000 индексов");
                            }
                        }
                    }
                }
            }

            // Сохранить оставшиеся записи
            if (!batch.isEmpty()) {
                indexRepository.saveAll(batch);
                log.debug("Сохранен остаточный батч из {} индексов", batch.size());
            }

            // Очистить кэш для этого сайта
            idfCache.clear();

            log.info("Расчет TF-IDF завершен для {} страниц сайта: {}", pages.size(), siteUrl);

        } catch (Exception e) {
            log.error("Ошибка при расчете TF-IDF для сайта {}: {}", siteUrl, e.getMessage());
        }

        // для большого количества лемм долгое вычисление
        /*try {
            // Получаем все страницы сайта
            List<PageEntity> pages = pageRepository.findBySite(siteEntity);
            int totalPages = pages.size();

            if (totalPages == 0) {
                log.info("Нет страниц для расчета rank у сайта: {}", siteUrl);
                return;
            }

            log.info("Расчет TF-IDF для сайта {}: {} страниц", siteUrl, totalPages);

            for (PageEntity page : pages) {
                String pageKey = page.getId() + "_" + siteUrl;
                Map<String, Integer> pageLemmas = pageLemmasCache.get(pageKey);

                if (pageLemmas != null && !pageLemmas.isEmpty()) {
                    for (Map.Entry<String, Integer> entry : pageLemmas.entrySet()) {
                        String lemmaText = entry.getKey();
                        int tf = entry.getValue(); // Term Frequency (частота на странице)

                        // Находим лемму в БД
                        LemmaEntity lemmaEntity = lemmaRepository.findBySiteAndLemma(siteEntity, lemmaText)
                                .orElse(null);

                        if (lemmaEntity != null && lemmaEntity.getFrequency() > 0) {
                            // Inverse Document Frequency
                            // IDF = log(общее_число_страниц / число_страниц_с_этой_леммой)
                            float idf = (float) Math.log((float) totalPages / lemmaEntity.getFrequency());

                            // TF-IDF = TF * IDF
                            float rank = tf * idf;

                            // Создаем или обновляем индекс
                            IndexEntity indexEntity = indexRepository
                                    .findByPageAndLemma(page, lemmaEntity)
                                    .orElseGet(() -> {
                                        IndexEntity newIndex = new IndexEntity();
                                        newIndex.setPage(page);
                                        newIndex.setLemma(lemmaEntity);
                                        newIndex.setRank_count(0f);
                                        return newIndex;
                                    });

                            indexEntity.setRank_count(rank);
                            indexRepository.save(indexEntity);

                            // Логирование для отладки
                            if (log.isDebugEnabled()) {
                                log.debug("Лемма '{}': tf={}, idf={:.4f}, rank={:.4f}, frequency={}",
                                        lemmaText, tf, idf, rank, lemmaEntity.getFrequency());
                            }
                        }
                    }
                }
            }

            log.info("Расчет TF-IDF завершен для {} страниц сайта: {}", pages.size(), siteUrl);

        } catch (Exception e) {
            log.error("Ошибка при расчете TF-IDF для сайта {}: {}", siteUrl, e.getMessage());
        }*/
    }

    private float calculateIdf(int totalPages, int lemmaFrequency) {
        // Быстрое вычисление с кэшированием
        return idfCache.computeIfAbsent(lemmaFrequency,
                freq -> (float) Math.log((float) totalPages / freq));
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

    public boolean stopIndexing() {
        if (activePools.isEmpty()) {
            log.warn("Нет активных индексаций для остановки");
            return false;
        }

        log.info("Получен запрос на остановку всех активных индексаций");

        // Копируем ключи для безопасной итерации
        List<String> sitesToStop = new ArrayList<>(activePools.keySet());
        boolean anyStopped = false;

        for (String siteUrl : sitesToStop) {
            // Устанавливаем флаг остановки
            stopFlags.put(siteUrl, true);

            ForkJoinPool pool = activePools.get(siteUrl);
            if (pool != null) {
                stopFlags.put(siteUrl, true); // pool.shutdownNow();
            }

            // Обновляем статус в БД
            SiteEntity siteEntity = activeSites.get(siteUrl);
            if (siteEntity != null) {
                updateSiteStatus(siteEntity, SiteStatus.FAILED,
                        "Индексация остановлена пользователем");
            }

            // Очищаем ресурсы - этот код уже есть в
            //cleanupResources(siteUrl);

            log.info("Индексация сайта {} остановлена", siteUrl);
            anyStopped = true;
        }

        return anyStopped;
    }

    @Transactional
    public boolean startIndexingAll() {
        List<ConfigSite> allConfigSites = configSiteRepository.findAll();

        if (allConfigSites.isEmpty()) {
            log.warn("Нет сайтов для индексации в config_site");
            return false;
        }

        log.info("Запуск индексации для всех сайтов. Всего: {}", allConfigSites.size());

        // Очищаем предыдущие потоки
        //siteIndexingThreads.clear();

        // Запускаем индексацию каждого сайта в основном потоке
        for (ConfigSite configSite : allConfigSites) {
            String siteUrl = configSite.getUrl();

            if (activePools.containsKey(siteUrl)) {
                log.info("Сайт {} уже индексируется, пропускаем", siteUrl);
                continue;
            }

            // Запускаем в отдельном потоке с поддержкой транзакций
            /*new Thread(() -> {
                transactionTemplate.execute(status -> {
                    try {
                        log.info("Начинаем индексацию сайта: {}", siteUrl);
                        return startIndexing(siteUrl);
                    } catch (Exception e) {
                        log.error("Ошибка при индексации сайта {}: {}", siteUrl, e.getMessage());
                        return false;
                    }
                });
            }).start();*/
            /*transactionTemplate.execute(status -> {
                startIndexing(siteUrl);
                return null;
            });*/

            Thread t = new Thread(() -> {
                try {
                    log.info("Начинаем индексацию сайта: {}", siteUrl);

                    transactionTemplate.execute(status -> {
                        try {
                            startIndexing(siteUrl);
                        } catch (Exception e) {
                            log.error("Ошибка при индексации сайта {}: {}", siteUrl, e.getMessage(), e);
                            throw e;
                        }
                        return null;
                    });

                    log.info("Завершена работа потока для сайта: {}", siteUrl);

                } catch (Exception e) {
                    log.error("Критическая ошибка потока для сайта: {}: {}", siteUrl, e.getMessage(), e);
                }
            });

            t.setName("Indexing-" + siteUrl);
            t.start();


            // Пауза
            try {
                Thread.sleep(configIndexing.getBatch_pause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Прервана пауза между запуском сайтов");
                break;
            }
        }

        return true;
    }

    private void cleanupResources(String siteUrl) {
        // Удаляем в правильном порядке
        stopFlags.remove(siteUrl);
        activePools.remove(siteUrl);
        visitedUrlsMap.remove(siteUrl);
        activeSites.remove(siteUrl);
        siteLemmasCache.remove(siteUrl);
        //pageLemmasCache.keySet().removeIf(key -> key.endsWith("_" + siteUrl));
        pageLemmasCache.keySet().removeIf(key -> key.startsWith(siteUrl));
        idfCache.clear();

        log.debug("Ресурсы очищены для сайта: {}", siteUrl);
    }

    @Transactional
    private void deleteSite(String siteUrl) {
        indexRepository.deleteBySiteUrl(siteUrl);
        lemmaRepository.deleteBySiteUrl(siteUrl);
        pageRepository.deleteBySiteUrl(siteUrl);
        siteRepository.deleteByUrl(siteUrl);
        log.info("Удалены данные сайта: {}", siteUrl);
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

            /*String normalizedUrl = normalizeUrl(url);
            if (!visitedUrls.add(normalizedUrl)) return;*/

            String path = extractPathUrl(url, siteEntity.getUrl());
            if (!visitedUrls.add(path)) return;

            // задержка из конфига
            try {
                int delay = random.nextInt(configIndexing.getDelay().getMax() -
                                configIndexing.getDelay().getMin() + 1) + configIndexing.getDelay().getMin();
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                Connection.Response response = Jsoup.connect(url)
                        //.userAgent("Mozilla/5.0 (compatible; SearchEngineBot/1.0)")
                        .userAgent(configIndexing.getUserAgent())
                        //.referrer("http://www.google.com")
                        .referrer(configIndexing.getReferrer())
                        .timeout(configIndexing.getTimeout())
                        .ignoreHttpErrors(true)
                        .execute();

                int statusCode = response.statusCode();
                String contentType = response.contentType();

                //String body = response.body(); // Получаем тело ответа
                // всегда сохраняем контент, независимо от типа
                //savePageToDatabase(url, statusCode, body, siteEntity);

                // JSON/изображения/etc - не сохраняем
                if (contentType != null && contentType.contains("text/html")) {
                    savePageToDatabase(url, statusCode, response.body(), siteEntity);
                }

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
                        if (stopFlags.getOrDefault(siteEntity.getUrl(), false)) {
                            return; // нормальная ситуация при STOP
                        }
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

                // УДАЛИТЬ trailing slash ДЛЯ ВСЕХ URL (даже если это корень)
                if (normalized.endsWith("/")) {
                    normalized = normalized.substring(0, normalized.length() - 1);
                }

                // Если после удаления слэша получилась пустая строка - это корень
                if (normalized.isEmpty()) {
                    normalized = "/";
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
                String path = extractPathUrl(url, siteEntity.getUrl());

                // Очистка контента от проблемных символов, если не удалось изменить кодировку БД
                String cleanContent = content;
                if (cleanContent != null) {
                    // Удаляем 4-байтовые символы UTF-8 (emoji и т.д.)
                    cleanContent = cleanContent.replaceAll("[^\\u0000-\\uFFFF]", "");
                }

                log.info("Сохранение страницы: {} (код: {}, длина: {})",
                        path, statusCode, cleanContent.length());

                // Извлекаем чистый текст из HTML
                String cleanText = extractTextHtml(content);
                //log.info("Чистый текст: {} символов", cleanText.length());

                //PageEntity pageEntity = existingPage.orElse(new PageEntity());
                PageEntity pageEntity = new PageEntity();
                pageEntity.setSite(siteEntity);
                pageEntity.setPath(path);
                pageEntity.setCode(statusCode);
                pageEntity.setContentHtml(cleanContent);
                pageEntity.setContentText(cleanText);

                pageRepository.save(pageEntity);

                Map<String, Integer> pageLemmas = lemmaService.extractLemmas(cleanText);

                /*String pageKey = savedPage.getId() + "_" + siteEntity.getUrl();
                pageLemmasCache.put(pageKey, pageLemmas);*/
                String pageKey = siteEntity.getUrl() + path;
                pageLemmasCache.put(pageKey, pageLemmas);

                // Только для НОВЫХ страниц обновляем frequency
                updateSiteLemmasCache(siteEntity.getUrl(), pageLemmas.keySet());

                log.info("Страница сохранена: {}", pageKey);
            } catch (Exception e) {
                log.error("Ошибка при сохранении страницы {}: {}", url, e.getMessage());
            }
        }

        private void updateSiteLemmasCache(String siteUrl, Set<String> uniqueLemmas) {
            siteLemmasCache.compute(siteUrl, (key, existingMap) -> {
                if (existingMap == null) {
                    Map<String, Integer> newMap = new ConcurrentHashMap<>();
                    for (String lemma : uniqueLemmas) {
                        newMap.put(lemma, 1);
                    }
                    return newMap;
                } else {
                    for (String lemma : uniqueLemmas) {
                        existingMap.merge(lemma, 1, Integer::sum);
                    }
                    return existingMap;
                }
            });
        }

        private String extractPathUrl(String fullUrl, String baseUrl) {
            try {
                URI fullUri = new URI(fullUrl);

                String path = fullUri.getPath();
                if (path == null || path.isEmpty() || path.equals("/")) {
                    path = "/";
                } else if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }

                String query = fullUri.getQuery();
                if (query != null && !query.isEmpty()) {
                    // Сортируем параметры запроса, чтобы ?year=2026&month=1 и ?month=1&year=2026 считались одинаково
                    List<String> params = Arrays.asList(query.split("&"));
                    Collections.sort(params);
                    path = path + "?" + String.join("&", params);
                }

                // Фрагмент не сохраняем (он удаляется в normalizeUrl)

                return path;
            } catch (URISyntaxException e) {
                return "/error";
            }
        }

        // метод удаления HTML тегов, скриптов, стилей
        private String extractTextHtml(String html) {
            try {
                Document doc = Jsoup.parse(html);

                // Удаляем скрипты, стили, комментарии
                doc.select("script, style, noscript, iframe, svg, canvas").remove();

                // Удаляем теги, оставляем только текст
                String text = doc.text();

                // Удаляем 4-байтовые UTF-8 символы (emoji)
                text = text.replaceAll("[^\\u0000-\\uFFFF]", "");

                // Убираем лишние пробелы и переносы строк
                text = text.replaceAll("\\s+", " ").trim();

                return text;

            } catch (Exception e) {
                log.warn("Ошибка при извлечении текста из HTML: {}", e.getMessage());
                return ""; // Возвращаем пустую строку при ошибке
            }
        }
    }

    public SiteStatus getSiteStatus(String siteUrl) {
        return siteRepository.findByUrl(siteUrl)
                .map(SiteEntity::getStatus)
                .orElse(SiteStatus.FAILED);
    }
}
