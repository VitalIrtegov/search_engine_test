package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.models.*;
import searchengine.repository.*;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    private final LemmaService lemmaService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    public Map<String, Object> search(String query, String siteUrl, int offset, int limit) {
        log.info("Search request: query='{}', site='{}'", query, siteUrl);

        // 1. Разбиваем запрос на леммы
        Set<String> queryLemmasSet = extractQueryLemmas(query);
        log.info("Extracted lemmas from query: {}", queryLemmasSet);

        if (queryLemmasSet.isEmpty()) {
            log.info("No lemmas extracted from query");
            return createEmptyResponse();
        }

        // 2. Определяем сайты для поиска
        List<SiteEntity> sites = getSitesForSearch(siteUrl);
        log.info("Found {} sites for search", sites.size());
        if (sites.isEmpty()) {
            log.info("No sites found for search");
            return createEmptyResponse();
        }

        // 3. Ищем леммы в базе
        List<LemmaEntity> foundLemmas = findLemmasInDatabase(queryLemmasSet, sites);
        log.info("Found {} lemmas in database: {}", foundLemmas.size(),
                foundLemmas.stream().map(LemmaEntity::getLemma).toList());

        if (foundLemmas.isEmpty()) {
            log.info("No matching lemmas found in database");
            return createEmptyResponse();
        }

        // 4. Фильтруем слишком частые леммы
        foundLemmas = filterTooFrequentLemmas(foundLemmas, sites);
        log.info("After filtering: {} lemmas", foundLemmas.size());

        if (foundLemmas.isEmpty()) {
            return createEmptyResponse();
        }

        // 5. Сортируем леммы по частоте (от редких к частым)
        foundLemmas.sort(Comparator.comparingInt(LemmaEntity::getFrequency));

        // 6. Ищем страницы по леммам
        List<PageEntity> foundPages = findPagesByLemmas(foundLemmas);
        log.info("Found {} pages", foundPages.size());

        if (foundPages.isEmpty()) {
            log.info("No pages found for the lemmas");
            return createEmptyResponse();
        }

        // 7. Рассчитываем релевантность
        List<Map<String, Object>> results = calculateRelevance(foundPages, foundLemmas, query);

        // 8. Сортируем по релевантности и пагинируем
        results = sortAndPaginate(results, offset, limit);

        log.info("Returning {} results", results.size());
        return createResponse(results, results.size());
    }

    private Set<String> extractQueryLemmas(String query) {
        Map<String, Integer> lemmasMap = lemmaService.extractLemmas(query);
        return lemmasMap.keySet();
    }

    private List<SiteEntity> getSitesForSearch(String siteUrl) {
        if (siteUrl == null || siteUrl.isEmpty()) {
            // Ищем по всем сайтам
            return siteRepository.findAll();
        } else {
            // Ищем по конкретному сайту
            return siteRepository.findByUrl(siteUrl)
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }
    }

    private List<LemmaEntity> findLemmasInDatabase(Set<String> queryLemmas, List<SiteEntity> sites) {
        List<LemmaEntity> result = new ArrayList<>();

        log.info("Looking for lemmas: {} in {} sites", queryLemmas, sites.size());

        for (SiteEntity site : sites) {
            log.info("Checking site: {}", site.getUrl());

            for (String lemmaText : queryLemmas) {
                log.info("Looking for lemma: '{}' in site: {}", lemmaText, site.getUrl());

                Optional<LemmaEntity> lemmaOpt = lemmaRepository.findBySiteAndLemma(site, lemmaText);
                if (lemmaOpt.isPresent()) {
                    LemmaEntity lemma = lemmaOpt.get();
                    log.info("Found lemma: {} with frequency: {}", lemma.getLemma(), lemma.getFrequency());
                    result.add(lemma);
                } else {
                    log.info("Lemma '{}' not found in site: {}", lemmaText, site.getUrl());
                }
            }
        }

        return result;
    }

    private List<LemmaEntity> filterTooFrequentLemmas(List<LemmaEntity> lemmas, List<SiteEntity> sites) {
        // Фильтруем леммы, которые встречаются на слишком большом проценте страниц
        List<LemmaEntity> filtered = new ArrayList<>();

        for (LemmaEntity lemma : lemmas) {
            long totalPages = pageRepository.countBySite(lemma.getSite());
            if (totalPages == 0) continue;

            float frequencyPercentage = (float) lemma.getFrequency() / totalPages * 100;

            // Исключаем леммы, которые встречаются на более чем 80% страниц
            if (frequencyPercentage < 80) {
                filtered.add(lemma);
            }
        }

        return filtered;
    }

    private List<PageEntity> findPagesByLemmas(List<LemmaEntity> lemmas) {
        if (lemmas.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("Starting to find pages for {} lemmas", lemmas.size());

        // Группируем леммы по сайтам
        Map<SiteEntity, List<LemmaEntity>> lemmasBySite = new HashMap<>();
        for (LemmaEntity lemma : lemmas) {
            SiteEntity site = lemma.getSite();
            lemmasBySite
                    .computeIfAbsent(site, k -> new ArrayList<>())
                    .add(lemma);
        }

        log.info("Lemmas grouped by {} sites", lemmasBySite.size());

        List<PageEntity> allPages = new ArrayList<>();

        // Ищем страницы для каждого сайта отдельно
        for (Map.Entry<SiteEntity, List<LemmaEntity>> entry : lemmasBySite.entrySet()) {
            SiteEntity site = entry.getKey();
            List<LemmaEntity> siteLemmas = entry.getValue();

            log.info("Processing site: {} with {} lemmas", site.getUrl(), siteLemmas.size());

            List<PageEntity> sitePages = findPagesForSite(siteLemmas);
            allPages.addAll(sitePages);
        }

        log.info("Total pages found across all sites: {}", allPages.size());
        return allPages;
    }

    private List<PageEntity> findPagesForSite(List<LemmaEntity> siteLemmas) {
        if (siteLemmas.isEmpty()) {
            return Collections.emptyList();
        }

        // Сортируем леммы сайта по частоте (от редких к частым)
        siteLemmas.sort(Comparator.comparingInt(LemmaEntity::getFrequency));

        LemmaEntity firstLemma = siteLemmas.get(0);
        List<IndexEntity> indexes = indexRepository.findByLemma(firstLemma);
        Set<PageEntity> pages = new HashSet<>();

        for (IndexEntity index : indexes) {
            pages.add(index.getPage());
        }

        log.info("Site '{}': found {} pages for first lemma '{}'",
                firstLemma.getSite().getUrl(), pages.size(), firstLemma.getLemma());

        // Фильтруем по остальным леммам этого же сайта
        for (int i = 1; i < siteLemmas.size() && !pages.isEmpty(); i++) {
            LemmaEntity lemma = siteLemmas.get(i);
            List<IndexEntity> lemmaIndexes = indexRepository.findByLemma(lemma);

            Set<PageEntity> lemmaPages = new HashSet<>();
            for (IndexEntity index : lemmaIndexes) {
                lemmaPages.add(index.getPage());
            }

            log.info("Site '{}': filtering by lemma '{}' with {} pages",
                    lemma.getSite().getUrl(), lemma.getLemma(), lemmaPages.size());

            pages.retainAll(lemmaPages);
            log.info("After filtering: {} pages remain", pages.size());
        }

        return new ArrayList<>(pages);
    }

    private List<Map<String, Object>> calculateRelevance(List<PageEntity> pages,
                                                         List<LemmaEntity> lemmas,
                                                         String query) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (pages.isEmpty() || lemmas.isEmpty()) {
            return results;
        }

        // Находим максимальную абсолютную релевантность
        float maxAbsRelevance = 0;
        Map<PageEntity, Float> pageRelevance = new HashMap<>();

        for (PageEntity page : pages) {
            float absRelevance = 0;

            // Суммируем ранги всех лемм на странице
            for (LemmaEntity lemma : lemmas) {
                Optional<IndexEntity> indexOpt = indexRepository.findByPageAndLemma(page, lemma);
                if (indexOpt.isPresent()) {
                    absRelevance += indexOpt.get().getRank_count();
                }
            }

            pageRelevance.put(page, absRelevance);
            maxAbsRelevance = Math.max(maxAbsRelevance, absRelevance);
        }

        // Рассчитываем относительную релевантность
        for (Map.Entry<PageEntity, Float> entry : pageRelevance.entrySet()) {
            PageEntity page = entry.getKey();
            float absRelevance = entry.getValue();
            float relativeRelevance = maxAbsRelevance > 0 ? absRelevance / maxAbsRelevance : 0;

            Map<String, Object> result = new HashMap<>();
            result.put("site", page.getSite().getUrl());
            result.put("siteName", page.getSite().getName());
            result.put("uri", page.getPath());
            result.put("title", extractTitle(page.getContentHtml()));
            result.put("snippet", generateSnippet(page.getContentText(), query));
            result.put("relevance", relativeRelevance);

            results.add(result);
        }

        return results;
    }

    private List<Map<String, Object>> sortAndPaginate(List<Map<String, Object>> results,
                                                      int offset, int limit) {
        // Сортируем по убыванию релевантности
        results.sort((a, b) -> {
            float relA = (float) a.get("relevance");
            float relB = (float) b.get("relevance");
            return Float.compare(relB, relA); // По убыванию
        });

        // Пагинация
        int end = Math.min(offset + limit, results.size());
        if (offset >= results.size()) {
            return Collections.emptyList();
        }

        return results.subList(offset, end);
    }

    private String extractTitle(String html) {
        if (html == null || html.isEmpty()) {
            return "No title";
        }

        // Простая логика извлечения title
        int titleStart = html.indexOf("<title>");
        int titleEnd = html.indexOf("</title>");

        if (titleStart != -1 && titleEnd != -1 && titleStart < titleEnd) {
            return html.substring(titleStart + 7, titleEnd).trim();
        }

        return "Untitled";
    }

    private String generateSnippet(String text, String query) {
        if (text == null || text.isEmpty()) {
            return "No content";
        }

        Set<String> queryWords = extractQueryLemmas(query);

        // Ищем любое слово из запроса в тексте
        for (String word : queryWords) {
            String wordLower = word.toLowerCase();
            String textLower = text.toLowerCase();

            int index = textLower.indexOf(wordLower);
            if (index != -1) {
                // Берем контекст вокруг найденного слова
                int start = Math.max(0, index - 100);
                int end = Math.min(text.length(), index + word.length() + 100);

                String snippet = text.substring(start, end);

                // Выделяем ВСЕ вхождения слова в сниппете
                String highlighted = snippet.replaceAll("(?i)" + Pattern.quote(word), "<b>$0</b>");

                // Добавляем многоточия если обрезали
                if (start > 0) highlighted = "..." + highlighted;
                if (end < text.length()) highlighted = highlighted + "...";

                return highlighted;
            }
        }

        // Если слова не найдены, берем начало текста
        return text.substring(0, Math.min(200, text.length())) + "...";
    }

    private Map<String, Object> createEmptyResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("result", true);
        response.put("count", 0);
        response.put("data", new ArrayList<>());
        return response;
    }

    private Map<String, Object> createResponse(List<Map<String, Object>> data, int totalCount) {
        Map<String, Object> response = new HashMap<>();
        response.put("result", true);
        response.put("count", totalCount);
        response.put("data", data);
        return response;
    }
}
