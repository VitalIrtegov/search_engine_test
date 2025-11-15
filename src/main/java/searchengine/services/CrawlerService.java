package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.*;
import searchengine.repository.ConfigSiteRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final ConfigSiteRepository configSiteRepository;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final CrawlerProperties props;

    private final Map<Integer, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();
    private final Map<Integer, ForkJoinPool> pools = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // ---------------------------------------------------------------------

    public void startCrawlingAll() {
        List<ConfigSite> list = configSiteRepository.findAll();
        for (ConfigSite cs : list) {
            startCrawlingForConfigSite(cs.getUrl());
        }
    }

    @Transactional
    public void startCrawlingForConfigSite(String configUrl) {

        ConfigSite config = configSiteRepository.findByUrl(configUrl)
                .orElseThrow(() -> new RuntimeException("ConfigSite not found: " + configUrl));

        SiteEntity site = siteRepository.findByUrl(config.getUrl())
                .orElseGet(() -> {
                    SiteEntity s = new SiteEntity();
                    s.setUrl(config.getUrl());
                    s.setName(config.getName());
                    s.setStatus(SiteStatus.FAILED);
                    s.setStatusTime(LocalDateTime.now());
                    return siteRepository.save(s);
                });

        startCrawlingForSite(site);
    }

    @Transactional
    public void startCrawlingForSite(SiteEntity site) {

        pageRepository.deleteAllBySiteId(site.getId());

        site.setStatus(SiteStatus.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(null);
        siteRepository.save(site);

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        stopFlags.put(site.getId(), stopFlag);

        ForkJoinPool pool = new ForkJoinPool(
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2)
        );
        pools.put(site.getId(), pool);

        String root = normalize(site.getUrl());

        Set<String> visited = ConcurrentHashMap.newKeySet();
        visited.add(root);

        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
        queue.add(root);

        CrawlTask task = new CrawlTask(root, site, visited, queue, stopFlag);
        pool.execute(task);

        pool.submit(() -> {
            try {
                task.join();

                if (stopFlag.get()) {
                    site.setStatus(SiteStatus.FAILED);
                    site.setLastError("Индексация остановлена пользователем");
                } else if (!SiteStatus.FAILED.equals(site.getStatus())) {
                    site.setStatus(SiteStatus.INDEXED);
                }

            } catch (Exception e) {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError(e.getMessage());
            } finally {
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);

                stopFlags.remove(site.getId());
                pools.remove(site.getId());
            }
        });
    }

    public void stopCrawlingForConfigSite(String configUrl) {
        ConfigSite cfg = configSiteRepository.findByUrl(configUrl)
                .orElseThrow(() -> new RuntimeException("ConfigSite not found: " + configUrl));

        siteRepository.findByUrl(cfg.getUrl()).ifPresent(this::stopCrawlingForSite);
    }

    public void stopCrawlingForSite(SiteEntity site) {

        AtomicBoolean stop = stopFlags.get(site.getId());
        if (stop != null) stop.set(true);

        ForkJoinPool pool = pools.get(site.getId());
        if (pool != null) pool.shutdownNow();

        site.setStatus(SiteStatus.FAILED);
        site.setLastError("Индексация остановлена пользователем");
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    public void stopCrawlingAll() {
        List<ConfigSite> list = configSiteRepository.findAll();
        for (ConfigSite cs : list) {
            siteRepository.findByUrl(cs.getUrl()).ifPresent(this::stopCrawlingForSite);
        }
    }

    // ---------------------------------------------------------------------

    private class CrawlTask extends RecursiveAction {

        private final String url;
        private final SiteEntity site;
        private final Set<String> visited;
        private final Queue<String> queue;
        private final AtomicBoolean stopFlag;

        public CrawlTask(String url, SiteEntity site, Set<String> visited, Queue<String> queue, AtomicBoolean stopFlag) {
            this.url = url;
            this.site = site;
            this.visited = visited;
            this.queue = queue;
            this.stopFlag = stopFlag;
        }

        @Override
        protected void compute() {

            if (stopFlag.get()) return;

            try {
                String path = extractPath(url);
                if (path == null) return;

                if (pageRepository.existsBySiteAndPath(site, path)) {
                    updateTime();
                    return;
                }

                int delay = props.getDelay().getMin()
                        + random.nextInt(props.getDelay().getMax() - props.getDelay().getMin());
                Thread.sleep(delay);

                Document doc = Jsoup.connect(url)
                        .userAgent(props.getUserAgent())
                        .referrer(props.getReferrer())
                        .timeout(props.getTimeout())
                        .ignoreHttpErrors(true)
                        .get();

                int code = doc.connection().response().statusCode();

                PageEntity page = new PageEntity();
                page.setSite(site);
                page.setPath(path);
                page.setCode(code);
                page.setContent(doc.outerHtml());
                pageRepository.save(page);

                updateTime();

                Elements links = doc.select("a[href]");
                List<CrawlTask> subtasks = new ArrayList<>();

                for (Element e : links) {
                    if (stopFlag.get()) break;

                    String href = e.attr("abs:href").split("#")[0];
                    if (href.isEmpty()) continue;

                    if (!sameHost(href, site.getUrl())) continue;

                    String normal = normalize(href);

                    if (visited.add(normal)) {
                        queue.add(normal);
                        subtasks.add(new CrawlTask(normal, site, visited, queue, stopFlag));
                    }
                }

                invokeAll(subtasks);

            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError("Ошибка: " + url + " — " + e.getMessage());
                updateTime();
                stopFlag.set(true);
            }
        }

        private void updateTime() {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }

        private String extractPath(String href) {
            try {
                URI u = new URI(href);
                String p = u.getPath();
                return (p == null || p.isEmpty()) ? "/" : p;
            } catch (Exception e) {
                return null;
            }
        }

        private boolean sameHost(String a, String b) {
            try {
                URI u1 = new URI(a);
                URI u2 = new URI(b);
                return Objects.equals(u1.getHost(), u2.getHost());
            } catch (Exception e) {
                return false;
            }
        }
    }

    private String normalize(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

