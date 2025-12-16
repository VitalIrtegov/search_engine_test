package searchengine.models;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "page", indexes = {
        @Index(name = "idx_path", columnList = "path")
})
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(columnDefinition = "VARCHAR(500) NOT NULL")
    private String path;

    @Column(nullable = false)
    private Integer code;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String contentHtml; // Полный HTML для сниппетов

    @Column(columnDefinition = "MEDIUMTEXT")
    private String contentText; // Чистый текст для поиска
}
