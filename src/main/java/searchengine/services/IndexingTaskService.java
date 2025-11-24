package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.ConfigCrawler;
import searchengine.models.*;
import searchengine.repository.SiteRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.IndexRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingTaskService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaService lemmaService;
    private final ConfigCrawler configCrawler;

    public CompletableFuture<Boolean> startIndexingTask(SiteEntity site) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                initializeIndexing(site);
                processIndexing(site);
                completeIndexing(site);
                return true;
            } catch (Exception e) {
                log.error("Indexing error for site: {}", site.getUrl(), e);
                handleIndexingError(site, "Ошибка индексации: " + e.getMessage());
                return false;
            }
        });
    }

    private void initializeIndexing(SiteEntity site) {
        cleanupSiteData(site.getUrl());
        updateSiteStatus(site, SiteStatus.INDEXING, null);
        log.info("Initialized indexing for site: {}", site.getUrl());
    }

    private void processIndexing(SiteEntity site) throws InterruptedException {
        Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
        Queue<String> urlQueue = new ConcurrentLinkedQueue<>();
        Random random = new Random();

        urlQueue.add(site.getUrl());
        visitedUrls.add(site.getUrl());

        while (!urlQueue.isEmpty() && visitedUrls.size() < 1000) {
            String url = urlQueue.poll();
            if (url == null) continue;

            try {
                // Задержка
                int delay = configCrawler.getDelay().getMin() +
                        random.nextInt(configCrawler.getDelay().getMax() -
                                configCrawler.getDelay().getMin());
                Thread.sleep(delay);

                // Обработка страницы
                processSinglePage(site, url, visitedUrls, urlQueue);
                updateStatusTime(site);

            } catch (IOException e) {
                log.warn("Failed to process page: {}", url, e);
            }
        }
    }

    private void processSinglePage(SiteEntity site, String url, Set<String> visitedUrls, Queue<String> urlQueue) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent(configCrawler.getUserAgent())
                .referrer(configCrawler.getReferrer())
                .timeout(configCrawler.getTimeout())
                .get();

        // Сохраняем и индексируем страницу
        PageEntity page = savePage(site, url, doc.html(), 200);
        indexPageContent(page, doc);

        // Обрабатываем ссылки
        processLinks(doc, site, visitedUrls, urlQueue);
    }

    private void processLinks(Document doc, SiteEntity site, Set<String> visitedUrls, Queue<String> urlQueue) {
        Elements links = doc.select("a[href]");
        String baseDomain = extractDomain(site.getUrl());

        for (Element link : links) {
            if (visitedUrls.size() >= 1000) break;

            String href = link.attr("abs:href");
            if (isValidLink(href, baseDomain) && visitedUrls.add(href)) {
                urlQueue.offer(href);
            }
        }
    }

    private void indexPageContent(PageEntity page, Document doc) {
        String title = doc.title();
        String bodyText = doc.body().text();
        String fullText = title + " " + bodyText;

        Map<String, Integer> lemmas = lemmaService.extractLemmas(fullText);
        saveLemmasAndIndex(page, lemmas);

        log.debug("Indexed page: {} ({} lemmas)", page.getPath(), lemmas.size());
    }

    // Транзакционные методы (маленькие, по 1-2 строки)
    private void cleanupSiteData(String siteUrl) {
        indexRepository.deleteBySiteUrl(siteUrl);
        lemmaRepository.deleteBySiteUrl(siteUrl);
        pageRepository.deleteBySiteUrl(siteUrl);
    }

    private void updateSiteStatus(SiteEntity site, SiteStatus status, String error) {
        site.setStatus(status);
        site.setStatusTime(LocalDateTime.now());
        if (error != null) {
            site.setLastError(error);
        }
        siteRepository.save(site);
    }

    private void updateStatusTime(SiteEntity site) {
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    private PageEntity savePage(SiteEntity site, String url, String content, int code) {
        PageEntity page = new PageEntity();
        page.setSite(site);
        page.setPath(extractPath(url));
        page.setCode(code);
        page.setContent(content);
        return pageRepository.save(page);
    }

    private void saveLemmasAndIndex(PageEntity page, Map<String, Integer> lemmas) {
        SiteEntity site = page.getSite();
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            LemmaEntity lemma = findOrCreateLemma(site, entry.getKey(), entry.getValue());
            createIndexEntry(page, lemma, entry.getValue());
        }
    }

    private LemmaEntity findOrCreateLemma(SiteEntity site, String lemmaText, int frequency) {
        LemmaEntity lemma = lemmaRepository.findBySiteAndLemma(site, lemmaText)
                .orElse(new LemmaEntity());
        lemma.setSite(site);
        lemma.setLemma(lemmaText);
        lemma.setFrequency(lemma.getFrequency() != null ?
                lemma.getFrequency() + frequency : frequency);
        return lemmaRepository.save(lemma);
    }

    private void createIndexEntry(PageEntity page, LemmaEntity lemma, int frequency) {
        IndexEntity index = new IndexEntity();
        index.setPage(page);
        index.setLemma(lemma);
        index.setRank_count(frequency * 1.0f);
        indexRepository.save(index);
    }

    private void completeIndexing(SiteEntity site) {
        updateSiteStatus(site, SiteStatus.INDEXED, null);
        log.info("Indexing completed for site: {}", site.getUrl());
    }

    private void handleIndexingError(SiteEntity site, String error) {
        updateSiteStatus(site, SiteStatus.FAILED, error);
    }

    // Вспомогательные методы
    private String extractDomain(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
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
            return url;
        }
    }

    private boolean isValidLink(String href, String baseDomain) {
        if (href == null || href.isEmpty() || href.startsWith("#") ||
                href.startsWith("mailto:") || href.startsWith("tel:")) {
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
