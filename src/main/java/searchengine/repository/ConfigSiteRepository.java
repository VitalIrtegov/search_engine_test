package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.models.ConfigSite;

import java.util.Optional;

@Repository
public interface ConfigSiteRepository extends JpaRepository<ConfigSite, Integer> {
    Optional<ConfigSite> findByUrl(String url);

    boolean existsByUrl(String url);

    boolean existsByName(String name);

    Optional<ConfigSite> findByName(String name);
}
