package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    /**
     * Найти страницу по сайту и пути
     */
    Optional<PageEntity> findBySiteAndPath(SiteEntity site, String path);

    /**
     * Найти все страницы сайта
     */
    List<PageEntity> findBySite(SiteEntity site);

    /**
     * Найти страницы сайта по коду ответа
     */
    List<PageEntity> findBySiteAndCode(SiteEntity site, Integer code);

    /**
     * Найти страницы по коду ответа
     */
    List<PageEntity> findByCode(Integer code);

    /**
     * Проверить существует ли страница с таким путем для сайта
     */
    boolean existsBySiteAndPath(SiteEntity site, String path);

    /**
     * Удалить все страницы сайта по URL сайта
     */
    @Modifying
    //@Query("DELETE FROM PageEntity p WHERE p.site.url = :siteUrl")
    @Query(value = "DELETE p FROM page p INNER JOIN site s ON p.site_id = s.id WHERE s.url = :siteUrl", nativeQuery = true)
    void deleteBySiteUrl(@Param("siteUrl") String siteUrl);

    /**
     * Удалить все страницы сайта
     */
    @Modifying
    @Query("DELETE FROM PageEntity p WHERE p.site = :site")
    void deleteBySite(@Param("site") SiteEntity site);

    /**
     * Получить количество страниц сайта
     */
    @Query("SELECT COUNT(p) FROM PageEntity p WHERE p.site = :site")
    long countBySite(@Param("site") SiteEntity site);

    /**
     * Получить количество страниц сайта по коду ответа
     */
    @Query("SELECT COUNT(p) FROM PageEntity p WHERE p.site = :site AND p.code = :code")
    long countBySiteAndCode(@Param("site") SiteEntity site, @Param("code") Integer code);

    /**
     * Найти все уникальные пути для сайта
     */
    @Query("SELECT p.path FROM PageEntity p WHERE p.site = :site")
    List<String> findPathsBySite(@Param("site") SiteEntity site);

    /**
     * Найти страницы с содержимым, содержащим текст
     */
    //@Query("SELECT p FROM PageEntity p WHERE p.content LIKE %:text%")
    //List<PageEntity> findByContentTextContaining(@Param("text") String text);
}
