package searchengine.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.site.AddSiteRequest;
import searchengine.dto.site.AddSiteResponse;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.site.DeleteSiteResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SiteService;
import searchengine.services.StatisticsService;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {
    private final StatisticsService statisticsService;
    private final SiteService siteService;
    private final IndexingService indexingService;

    public ApiController(StatisticsService statisticsService, SiteService siteService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.siteService = siteService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @PostMapping("/sites")
    public ResponseEntity<AddSiteResponse> addSite(@Valid @RequestBody AddSiteRequest request) {
        AddSiteResponse response = siteService.addSite(request);
        return ResponseEntity.status(response.isResult() ? 200 : 400).body(response);
    }

    @DeleteMapping("/sites")
    public ResponseEntity<DeleteSiteResponse> deleteSite(@RequestParam String siteName) {
        DeleteSiteResponse response = siteService.deleteSite(siteName);
        return ResponseEntity.status(response.isResult() ? 200 : 400).body(response);
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing(@RequestParam(required = false) String site) {
        System.out.println("ApiController[СТАРТ]: " + site);

        IndexingResponse response = new IndexingResponse();

        boolean result = indexingService.startIndexing(site);
        response.setResult(result);
        response.setMessage(result ? "Started" : "Failed");

        // ЗАГЛУШКА - всегда возвращаем успех
        /*response.setResult(true);
        response.setMessage("Indexing started for site: " + site);*/
        return ResponseEntity.ok(response);
    }

    @GetMapping("/startIndexingAll")
    public ResponseEntity<IndexingResponse> startIndexingAll() {
        IndexingResponse response = new IndexingResponse();

        boolean result = indexingService.startIndexingAll();
        response.setResult(result);
        response.setMessage(result ?
                "Indexing started for all sites" :
                "Failed to start indexing for all sites");

        // ЗАГЛУШКА - всегда возвращаем успех
        /*response.setResult(true);
        response.setMessage("Indexing started for all sites");*/
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        IndexingResponse response = new IndexingResponse();

        // Останавливаем индексацию для всех активных сайтов
        boolean result = indexingService.stopIndexing();
        response.setResult(result);
        response.setMessage(result ?
                "Indexing stopped successfully for all sites" :
                "No active indexing to stop");

        // ЗАГЛУШКА - всегда возвращаем успех
        //response.setResult(true);
        //response.setMessage("Indexing stopped successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam String url) {
        Map<String, Object> response = new HashMap<>();

        // ЗАГЛУШКА - всегда возвращаем успех
        response.put("result", true);
        response.put("message", "Page indexing started for: " + url);
        return ResponseEntity.ok(response);
    }
}
