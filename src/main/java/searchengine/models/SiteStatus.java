package searchengine.models;

public enum SiteStatus {
    INDEXING,  // В процессе индексации
    INDEXED,   // Успешно проиндексирован
    CRAWLING,  // Обход сайта в процессе
    CRAWLED,   // Обход сайта завершен
    FAILED     // Ошибка индексации
}
