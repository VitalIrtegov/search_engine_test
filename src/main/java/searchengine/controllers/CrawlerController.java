package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.services.CrawlerService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/crawling")
public class CrawlerController {

    private final CrawlerService crawlerService;

    @PostMapping(path = "/start")
    public ResponseEntity<?> start(@RequestParam(name = "site", required = false) String url) {
        try {
            if (url == null || url.isBlank() || url.equals("all")) {
                crawlerService.startCrawlingAll();
                return ResponseEntity.ok("Crawling started for ALL config sites");
            }
            crawlerService.startCrawlingForConfigSite(url);
            return ResponseEntity.ok("Crawling started for " + url);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping(path = "/stop")
    public ResponseEntity<?> stop(@RequestParam(name = "site", required = false) String url) {
        try {
            if (url == null || url.isBlank() || url.equals("all")) {
                crawlerService.stopCrawlingAll();
                return ResponseEntity.ok("All crawling tasks stopped");
            }
            crawlerService.stopCrawlingForConfigSite(url);
            return ResponseEntity.ok("Stopped: " + url);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}

