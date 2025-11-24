package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.models.SiteEntity;
import searchengine.models.SiteStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {
    /**
     * Найти сайт по URL
     */
    Optional<SiteEntity> findByUrl(String url);

    /**
     * Найти сайт по имени
     */
    Optional<SiteEntity> findByName(String name);

    /**
     * Найти все сайты по статусу
     */
    List<SiteEntity> findByStatus(SiteStatus status);

    /**
     * Найти все сайты с статусом INDEXING (активные процессы индексации)
     */
    @Query("SELECT s FROM SiteEntity s WHERE s.status = 'INDEXING'")
    List<SiteEntity> findIndexingSites();

    /**
     * Обновить статус сайта
     */
    @Modifying
    @Query("UPDATE SiteEntity s SET s.status = :status, s.statusTime = :statusTime WHERE s.id = :id")
    void updateStatus(@Param("id") Integer id,
                      @Param("status") SiteStatus status,
                      @Param("statusTime") LocalDateTime statusTime);

    /**
     * Обновить статус и ошибку сайта
     */
    @Modifying
    @Query("UPDATE SiteEntity s SET s.status = :status, s.statusTime = :statusTime, s.lastError = :lastError WHERE s.id = :id")
    void updateStatusWithError(@Param("id") Integer id,
                               @Param("status") SiteStatus status,
                               @Param("statusTime") LocalDateTime statusTime,
                               @Param("lastError") String lastError);

    /**
     * Обновить только время статуса
     */
    @Modifying
    @Query("UPDATE SiteEntity s SET s.statusTime = :statusTime WHERE s.id = :id")
    void updateStatusTime(@Param("id") Integer id, @Param("statusTime") LocalDateTime statusTime);

    /**
     * Проверить существует ли сайт с таким URL
     */
    boolean existsByUrl(String url);

    /**
     * Удалить сайт по URL
     */
    @Modifying
    @Query("DELETE FROM SiteEntity s WHERE s.url = :url")
    void deleteByUrl(@Param("url") String url);
}
