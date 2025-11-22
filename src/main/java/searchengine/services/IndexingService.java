package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {
    public boolean startIndexing(String siteName) {
        return true;
    }

    public boolean startIndexingAll() {
        return true;
    }

    public boolean stopIndexing() {
        return true;
    }

}
