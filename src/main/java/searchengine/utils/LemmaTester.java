package searchengine.utils;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;

import java.util.*;

public class LemmaTester {
    /*public static void main(String[] args) {
        try {
            LuceneMorphology russianMorphology = new RussianLuceneMorphology();
            LuceneMorphology englishMorphology = new EnglishLuceneMorphology();

            String testText = "Beautiful cats play with ball. Красивые кошки играют с мячом. " +
                    "Dogs also like to play. Собаки тоже любят играть. " +
                    "The quick brown fox jumps over the lazy dog. " +
                    "Быстрая коричневая лиса прыгает через ленивую собаку. " +
                    "Programming and программирование are interesting activities. " +
                    "Java and Java - powerful technologies.";

            System.out.println("ИСХОДНЫЙ ТЕКСТ:");
            System.out.println(testText);
            System.out.println("\n" + "=".repeat(80) + "\n");

            Map<String, Integer> lemmas = extractMultiLanguageLemmas(testText, russianMorphology, englishMorphology);

            Map<String, Integer> russianLemmas = new HashMap<>();
            Map<String, Integer> englishLemmas = new HashMap<>();

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemma = entry.getKey();
                if (isCyrillic(lemma)) {
                    russianLemmas.put(lemma, entry.getValue());
                } else {
                    englishLemmas.put(lemma, entry.getValue());
                }
            }

            System.out.println("АНГЛИЙСКИЕ ЛЕММЫ:");
            System.out.println("-".repeat(40));
            englishLemmas.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .forEach(entry ->
                            System.out.printf("EN: %-20s : %d раз%n", entry.getKey(), entry.getValue())
                    );

            System.out.println("\nРУССКИЕ ЛЕММЫ:");
            System.out.println("-".repeat(40));
            russianLemmas.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .forEach(entry ->
                            System.out.printf("RU: %-20s : %d раз%n", entry.getKey(), entry.getValue())
                    );

            System.out.println("\n" + "=".repeat(80));
            System.out.printf("Всего английских лемм: %d%n", englishLemmas.size());
            System.out.printf("Всего русских лемм: %d%n", russianLemmas.size());
            System.out.printf("Всего уникальных лемм: %d%n", lemmas.size());

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Map<String, Integer> extractMultiLanguageLemmas(String text,
                                                                  LuceneMorphology russianMorph,
                                                                  LuceneMorphology englishMorph) {
        Map<String, Integer> lemmas = new HashMap<>();

        // сохраняем заглавные буквы для английского и русского
        String cleanText = text
                .replaceAll("<[^>]+>", " ")  // Удаляем HTML теги
                .replaceAll("&[^;]+;", " ")  // Удаляем HTML entities
                .replaceAll("[^a-zA-Zа-яёА-ЯЁ\\s]", " ")  // Сохраняем буквы обоих языков
                .replaceAll("\\s+", " ")     // Заменяем множественные пробелы
                .trim();

        System.out.println("ОЧИЩЕННЫЙ ТЕКСТ:");
        System.out.println(cleanText);
        System.out.println("\n" + "-".repeat(80) + "\n");

        System.out.println("ПРОЦЕСС ЛЕММАТИЗАЦИИ:");
        System.out.println("-".repeat(50));

        String[] words = cleanText.split("\\s+");

        for (String word : words) {
            if (word.length() < 2 || isStopWord(word.toLowerCase())) continue;

            String lowerWord = word.toLowerCase();
            boolean isRussian = isCyrillic(lowerWord);
            boolean isEnglish = isLatin(lowerWord);

            if (!isRussian && !isEnglish) continue;

            try {
                LuceneMorphology morphology = isRussian ? russianMorph : englishMorph;
                List<String> normalForms = morphology.getNormalForms(lowerWord);

                if (!normalForms.isEmpty() && !isFunctionalWord(morphology, lowerWord)) {
                    String lemma = normalForms.get(0);
                    lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);

                    String lang = isRussian ? "RU" : "EN";
                    System.out.printf("%s: '%-12s' → '%-15s'%n", lang, word, lemma);
                }
            } catch (Exception e) {
                System.out.printf("Не распознано: '%s'%n", word);
            }
        }

        return lemmas;
    }

    private static boolean isCyrillic(String word) {
        return word.matches("[а-яё]+");
    }

    private static boolean isLatin(String word) {
        return word.matches("[a-z]+");
    }

    private static boolean isFunctionalWord(LuceneMorphology morphology, String word) {
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

    private static boolean isStopWord(String word) {
        Set<String> stopWords = new HashSet<>();

        // Русские стоп-слова
        Collections.addAll(stopWords,
                "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то",
                "все", "она", "так", "его", "но", "да", "ты", "к", "у", "же", "вы", "за",
                "бы", "по", "только", "ее", "мне", "было", "вот", "от", "меня", "еще", "нет",
                "о", "из", "ему", "теперь", "когда", "даже", "ну", "ли", "если", "уже",
                "или", "ни", "быть", "был", "него", "до", "вас", "нибудь", "опять", "уж",
                "с", "то", "же", "все", "это", "так", "как", "но", "на", "по", "из", "от"
        );

        // Английские стоп-слова
        Collections.addAll(stopWords,
                "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for", "of",
                "with", "by", "from", "up", "about", "into", "through", "during", "before",
                "after", "above", "below", "between", "among", "is", "are", "was", "were",
                "be", "been", "being", "have", "has", "had", "do", "does", "did", "will",
                "would", "shall", "should", "may", "might", "must", "can", "could", "this",
                "that", "these", "those", "i", "you", "he", "she", "it", "we", "they",
                "the", "with", "also", "over"
        );

        return stopWords.contains(word.toLowerCase());
    }*/
}
