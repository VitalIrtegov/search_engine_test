package searchengine.dto.statistics;

import lombok.Data;
import java.util.List;

@Data
public class StatisticsResponse {
    private boolean result;
    private int totalSites;
    private int totalPages;
    private int totalLemmas;
    private boolean indexing;
    private List<StatisticsSite> sites;
}
