package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;
import javax.transaction.Transactional;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    boolean existsBySiteAndPath(SiteEntity site, String path);
    Optional<PageEntity> findBySiteAndPath(SiteEntity site, String path);

    @Modifying
    @Transactional
    @Query("DELETE FROM PageEntity p WHERE p.site.id = :siteId")
    void deleteAllBySiteId(int siteId);
}
