package dev.demo.jobboard.orchestrator.config;

import dev.demo.jobboard.orchestrator.activity.CrawlActivities;
import dev.demo.jobboard.orchestrator.activity.CrewActivities;
import dev.demo.jobboard.orchestrator.activity.McpActivities;
import dev.demo.jobboard.orchestrator.activity.NotifyActivities;
import dev.demo.jobboard.orchestrator.activity.StreamActivities;
import dev.demo.jobboard.orchestrator.activity.impl.CrawlActivitiesImpl;
import dev.demo.jobboard.orchestrator.activity.impl.CrewActivitiesImpl;
import dev.demo.jobboard.orchestrator.activity.impl.McpActivitiesImpl;
import dev.demo.jobboard.orchestrator.activity.impl.StreamActivitiesImpl;
import dev.demo.jobboard.orchestrator.mcp.McpClient;
import dev.demo.jobboard.orchestrator.mcp.McpClientStub;
import dev.demo.jobboard.orchestrator.sse.SseEventBus;
import dev.demo.jobboard.orchestrator.workflow.impl.AnalysisWorkflowImpl;
import dev.demo.jobboard.orchestrator.workflow.impl.CrawlWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class TemporalConfig {

  public static final String JOB_BOARD_TASK_QUEUE = "jobboard-tq";

  @Bean
  public WorkflowServiceStubs workflowServiceStubs(
    org.springframework.core.env.Environment env
  ) {
    String target = env.getProperty("temporal.server", "127.0.0.1:7233");
    return io.temporal.serviceclient.WorkflowServiceStubs.newServiceStubs(
      io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
        .setTarget(target)
        .build()
    );
  }

  @Bean
  public WorkflowClient workflowClient(WorkflowServiceStubs service) {
    return WorkflowClient.newInstance(service);
  }

  // Provide an McpClient bean; choose stub or process based on config
  @Bean
  public McpClient mcpClient(McpConfig cfg, SseEventBus bus) {
    if (cfg.isEnableDirectExecution()) {
      // Use the process-backed client
      return new dev.demo.jobboard.orchestrator.mcp.McpClientProcess(cfg, bus);
    } else {
      // Default stub
      return new McpClientStub();
    }
  }

  // Activity beans - available for both API and worker
  @Bean
  public McpActivities mcpActivities(McpClient mcpClient) {
    return new McpActivitiesImpl(mcpClient);
  }

  @Bean
  public CrewActivities crewActivities(SseEventBus bus) {
    return new CrewActivitiesImpl(bus);
  }

  @Bean
  public CrawlActivities crawlActivity(McpClient mcpClient, SseEventBus bus) {
    return new CrawlActivitiesImpl(mcpClient, bus);
  }

  @Bean
  public StreamActivities streamActivities(SseEventBus eventBus) {
    return new StreamActivitiesImpl(eventBus);
  }

  @Bean
  public dev.demo.jobboard.orchestrator.activity.NotifyActivities notifyActivities(
    org.springframework.core.env.Environment env
  ) {
    // UI/API base URL where the controller listens
    String baseUrl = env.getProperty("api.baseUrl", "http://localhost:8082");
    return new dev.demo.jobboard.orchestrator.activity.impl.NotifyActivitiesImpl(
      baseUrl
    );
  }

  // Create and start worker(s) - available by default for development
  @Bean
  public WorkerFactory workerFactory(
    WorkflowClient client,
    McpActivities mcpActivities,
    CrewActivities crewActivities,
    CrawlActivities crawlActivity,
    StreamActivities streamActivities,
    NotifyActivities notifyActivities
  ) {
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(JOB_BOARD_TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(
      CrawlWorkflowImpl.class,
      AnalysisWorkflowImpl.class
    );
    worker.registerActivitiesImplementations(
      mcpActivities,
      crewActivities,
      crawlActivity,
      streamActivities,
      notifyActivities
    );

    factory.start();
    return factory;
  }
}
