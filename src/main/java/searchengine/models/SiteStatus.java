package searchengine.models;

public enum SiteStatus {
    INDEXING,  // В процессе индексации
    INDEXED,   // Успешно проиндексирован
    FAILED     // Ошибка индексации
}
