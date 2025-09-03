package dev.demo.jobboard.orchestrator.activity;

import dev.demo.jobboard.orchestrator.dto.CrawlBatchResult;
import dev.demo.jobboard.orchestrator.dto.CrawlRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface CrawlActivity {

    @ActivityMethod
    CrawlBatchResult fetchBatch(CrawlRequest req, int page);
}