package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import searchengine.dto.site.AddSiteRequest;
import searchengine.dto.site.AddSiteResponse;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.site.DeleteSiteResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.SiteService;
import searchengine.services.StatisticsService;

import javax.validation.Valid;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StatisticsService statisticsService;
    private final SiteService siteService;

    public ApiController(StatisticsService statisticsService, SiteService siteService) {
        this.statisticsService = statisticsService;
        this.siteService = siteService;
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
}
