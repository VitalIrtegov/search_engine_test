package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.models.ConfigSite;

@Repository
public interface ConfigSiteRepository extends JpaRepository<ConfigSite, Long> {
}
