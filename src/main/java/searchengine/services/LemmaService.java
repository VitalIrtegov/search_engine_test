package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.models.*;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LemmaService {
    private LuceneMorphology russianMorphology; // анализатор (инициализируются в @PostConstruct)
    private LuceneMorphology englishMorphology; // анализатор (инициализируются в @PostConstruct)

    @PostConstruct
    public void init() throws Exception { // После создания бина Spring вызывает @PostConstruct метод
        this.russianMorphology = new RussianLuceneMorphology(); // Создаются экземпляры морфологических анализаторов
        this.englishMorphology = new EnglishLuceneMorphology(); // Создаются экземпляры морфологических анализаторов
    }

    public Map<String, Integer> extractLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>(); // HashMap для хранения лемм и их частот
        String cleanText = cleanHtml(text); // Очищается HTML из текста (удаляются теги, entities)
        String[] words = cleanText.toLowerCase().split("\\s+"); // Текст приводится к нижнему регистру и разбивается на слова по пробелам

        // Цикл обработки слов
        for (String word : words) {
            if (word.length() < 2 || isStopWord(word)) continue; // Пропускаем короткие слова (< 2 символов) и стоп-слова

            boolean isRussian = word.matches("[а-яё]+"); // только русские буквы
            boolean isEnglish = word.matches("[a-z]+"); // только английские буквы
            if (!isRussian && !isEnglish) continue; // Пропускаем слова не подходящие ни под один язык

            try { // Процесс лемматизации
                LuceneMorphology morphology = isRussian ? russianMorphology : englishMorphology; // Выбираем анализатор по языку слова
                List<String> normalForms = morphology.getNormalForms(word);
                if (!normalForms.isEmpty() && !isFunctionalWord(morphology, word)) { // Проверяем что слово не служебное (не предлог/союз и т.д.)
                    String lemma = normalForms.get(0);
                    lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1); // Берем первую нормальную форму и увеличиваем счетчик в Map
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return lemmas;
    }

    // очистка Html
    private String cleanHtml(String html) {
        return html.replaceAll("<[^>]+>", " ") // удаляет HTML теги
                .replaceAll("&[^;]+;", " ") // удаляет HTML entities (  и т.д.)
                .replaceAll("[^a-zA-Zа-яёА-ЯЁ\\s]", " ") // оставляет только буквы и пробелы
                .replaceAll("\\s+", " ") // заменяет множественные пробелы на один
                .trim(); // обрезает пробелы по краям
    }

    // Определение служебных слов
    private boolean isFunctionalWord(LuceneMorphology morphology, String word) {
        try {
            String info = morphology.getMorphInfo(word).toString().toUpperCase();
            if (morphology instanceof RussianLuceneMorphology) {
                return info.contains("МЕЖД") || info.contains("ПРЕДЛ") || info.contains("СОЮЗ") ||
                        info.contains("МС") || info.contains("ЧАСТ") || info.contains("МЕСТОИМ");
            } else {
                return info.contains("PREP") || info.contains("CONJ") || info.contains("ARTICLE") ||
                        info.contains("PRON") || info.contains("PART") || info.contains("DET");
            }
        } catch (Exception e) {
            return false;
        }
    }

    // список стоп слов для обоих языков
    private boolean isStopWord(String word) {
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                // Русские стоп-слова (50+)
                "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то",
                "все", "она", "так", "его", "но", "да", "ты", "к", "у", "же", "вы", "за",
                "бы", "по", "только", "ее", "мне", "было", "вот", "от", "меня", "еще", "нет",
                "о", "из", "ему", "теперь", "когда", "даже", "ну", "ли", "если", "уже",
                "или", "ни", "быть", "был", "него", "до", "вас", "нибудь", "опять", "уж",

                // Английские стоп-слова (40+)
                "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for", "of",
                "with", "by", "from", "up", "about", "into", "through", "during", "before",
                "after", "above", "below", "between", "among", "is", "are", "was", "were",
                "be", "been", "being", "have", "has", "had", "do", "does", "did", "will",
                "would", "shall", "should", "may", "might", "must", "can", "could"
        ));
        return stopWords.contains(word);
    }
}
