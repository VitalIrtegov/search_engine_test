package searchengine.models;

public enum SiteStatus {
    INDEXING,
    INDEXED,
    CRAWLING,      // Обход сайта в процессе
    CRAWLED,        // Обход сайта завершен
    FAILED
}
