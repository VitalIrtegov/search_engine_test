package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.models.LemmaEntity;
import searchengine.models.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository  extends JpaRepository<LemmaEntity, Integer> {
    /**
     * Найти лемму по сайту и тексту леммы
     */
    Optional<LemmaEntity> findBySiteAndLemma(SiteEntity site, String lemma);

    /**
     * Найти все леммы сайта
     */
    List<LemmaEntity> findBySite(SiteEntity site);

    /**
     * Найти леммы по тексту (для поиска по всем сайтам)
     */
    List<LemmaEntity> findByLemma(String lemma);

    /**
     * Найти леммы сайта отсортированные по частоте (возрастание)
     */
    @Query("SELECT l FROM LemmaEntity l WHERE l.site = :site ORDER BY l.frequency ASC")
    List<LemmaEntity> findBySiteOrderByFrequencyAsc(@Param("site") SiteEntity site);

    /**
     * Найти леммы сайта отсортированные по частоте (убывание)
     */
    @Query("SELECT l FROM LemmaEntity l WHERE l.site = :site ORDER BY l.frequency DESC")
    List<LemmaEntity> findBySiteOrderByFrequencyDesc(@Param("site") SiteEntity site);

    /**
     * Удалить все леммы сайта по URL сайта
     */
    @Modifying
    //@Query("DELETE FROM LemmaEntity l WHERE l.site.url = :siteUrl")
    @Query(value = "DELETE l FROM lemma l " +
            "INNER JOIN site s ON l.site_id = s.id " +
            "WHERE s.url = :siteUrl", nativeQuery = true)
    void deleteBySiteUrl(@Param("siteUrl") String siteUrl);

    /**
     * Удалить все леммы сайта
     */
    @Modifying
    @Query("DELETE FROM LemmaEntity l WHERE l.site = :site")
    void deleteBySite(@Param("site") SiteEntity site);

    /**
     * Получить количество лемм сайта
     */
    @Query("SELECT COUNT(l) FROM LemmaEntity l WHERE l.site = :site")
    long countBySite(@Param("site") SiteEntity site);

    /**
     * Найти леммы с частотой больше указанной
     */
    @Query("SELECT l FROM LemmaEntity l WHERE l.site = :site AND l.frequency > :frequency")
    List<LemmaEntity> findBySiteAndFrequencyGreaterThan(@Param("site") SiteEntity site,
                                                        @Param("frequency") Integer frequency);

    /**
     * Найти леммы с частотой меньше указанной
     */
    @Query("SELECT l FROM LemmaEntity l WHERE l.site = :site AND l.frequency < :frequency")
    List<LemmaEntity> findBySiteAndFrequencyLessThan(@Param("site") SiteEntity site,
                                                     @Param("frequency") Integer frequency);
}
