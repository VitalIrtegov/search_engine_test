package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.models.IndexEntity;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    /**
     * Найти все записи индекса для леммы
     */
    List<IndexEntity> findByLemma(LemmaEntity lemma);

    /**
     * Найти все записи индекса для страницы
     */
    List<IndexEntity> findByPage(PageEntity page);

    /**
     * Найти запись индекса по странице и лемме
     */
    @Query("SELECT i FROM IndexEntity i WHERE i.page = :page AND i.lemma = :lemma")
    Optional<IndexEntity> findByPageAndLemma(@Param("page") PageEntity page,
                                             @Param("lemma") LemmaEntity lemma);

    /**
     * Удалить все записи индекса по URL сайта
     */
    @Modifying
    @Query("DELETE FROM IndexEntity i WHERE i.page.site.url = :siteUrl")
    void deleteBySiteUrl(@Param("siteUrl") String siteUrl);

    /**
     * Удалить все записи индекса для страницы
     */
    @Modifying
    @Query("DELETE FROM IndexEntity i WHERE i.page = :page")
    void deleteByPage(@Param("page") PageEntity page);

    /**
     * Удалить все записи индекса для леммы
     */
    @Modifying
    @Query("DELETE FROM IndexEntity i WHERE i.lemma = :lemma")
    void deleteByLemma(@Param("lemma") LemmaEntity lemma);

    /**
     * Получить количество записей индекса для страницы
     */
    @Query("SELECT COUNT(i) FROM IndexEntity i WHERE i.page = :page")
    long countByPage(@Param("page") PageEntity page);

    /**
     * Получить количество записей индекса для леммы
     */
    @Query("SELECT COUNT(i) FROM IndexEntity i WHERE i.lemma = :lemma")
    long countByLemma(@Param("lemma") LemmaEntity lemma);

    /**
     * Найти записи индекса с рангом больше указанного
     */
    @Query("SELECT i FROM IndexEntity i WHERE i.rank_count > :rank")
    List<IndexEntity> findByRankGreaterThan(@Param("rank") Float rank);

    /**
     * Найти записи индекса с рангом меньше указанного
     */
    @Query("SELECT i FROM IndexEntity i WHERE i.rank_count < :rank")
    List<IndexEntity> findByRankLessThan(@Param("rank") Float rank);

    /**
     * Найти записи индекса для списка лемм и страницы
     */
    @Query("SELECT i FROM IndexEntity i WHERE i.page = :page AND i.lemma IN :lemmas")
    List<IndexEntity> findByPageAndLemmas(@Param("page") PageEntity page,
                                          @Param("lemmas") List<LemmaEntity> lemmas);
}
