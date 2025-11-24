package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.models.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.IndexRepository;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LemmaService {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private LuceneMorphology russianMorphology;
    private LuceneMorphology englishMorphology;

    @PostConstruct
    public void init() throws Exception {
        this.russianMorphology = new RussianLuceneMorphology();
        this.englishMorphology = new EnglishLuceneMorphology();
    }

    public Map<String, Integer> extractLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        String cleanText = cleanHtml(text);
        String[] words = cleanText.toLowerCase().split("\\s+");

        for (String word : words) {
            if (word.length() < 2 || isStopWord(word)) continue;

            boolean isRussian = word.matches("[а-яё]+");
            boolean isEnglish = word.matches("[a-z]+");
            if (!isRussian && !isEnglish) continue;

            try {
                LuceneMorphology morphology = isRussian ? russianMorphology : englishMorphology;
                List<String> normalForms = morphology.getNormalForms(word);
                if (!normalForms.isEmpty() && !isFunctionalWord(morphology, word)) {
                    String lemma = normalForms.get(0);
                    lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return lemmas;
    }

    private String cleanHtml(String html) {
        return html.replaceAll("<[^>]+>", " ")
                .replaceAll("&[^;]+;", " ")
                .replaceAll("[^a-zA-Zа-яёА-ЯЁ\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isFunctionalWord(LuceneMorphology morphology, String word) {
        try {
            String info = morphology.getMorphInfo(word).toString().toUpperCase();
            if (morphology instanceof RussianLuceneMorphology) {
                return info.contains("МЕЖД") || info.contains("ПРЕДЛ") || info.contains("СОЮЗ");
            } else {
                return info.contains("PREP") || info.contains("CONJ") || info.contains("ARTICLE");
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("и", "в", "во", "не", "что", "он", "на", "я", "с", "со",
                "the", "and", "or", "but", "in", "on", "at", "to", "for");
        return stopWords.contains(word);
    }
}
