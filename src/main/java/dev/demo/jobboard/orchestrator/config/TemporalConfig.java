package dev.demo.jobboard.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import dev.demo.jobboard.orchestrator.activity.CrewActivities;
import dev.demo.jobboard.orchestrator.activity.McpActivities;
import dev.demo.jobboard.orchestrator.activity.StreamActivities;
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

@Configuration
public class TemporalConfig {

  public static final String TASK_QUEUE = "demo-tq";

  @Bean
  public WorkflowServiceStubs workflowServiceStubs() {
    return WorkflowServiceStubs.newLocalServiceStubs();
  }

  @Bean
  public WorkflowClient workflowClient(WorkflowServiceStubs service) {
    return WorkflowClient.newInstance(service);
  }

  // Provide an McpClient bean (replace with real implementation if available)
  @Bean
  public McpClient mcpClient() {
    return new McpClientStub();
  }

  // Worker-only beans
  @Bean
  @Profile("worker")
  public McpActivities mcpActivities(McpClient mcpClient, SseEventBus bus) {
    return new McpActivitiesImpl(mcpClient, bus);
  }

  @Bean
  @Profile("worker")
  public CrewActivities crewActivities() {
    return new CrewActivitiesImpl();
  }

  @Bean
  @Profile("worker")
  public StreamActivities streamActivities() {
    return new StreamActivitiesImpl();
  }

  // Create and start worker(s) only when profile=worker
  @Bean
  @Profile("worker")
  public WorkerFactory workerFactory(
      WorkflowClient client,
      McpActivities mcpActivities,
      CrewActivities crewActivities,
      StreamActivities streamActivities
  ) {
    WorkerFactory factory = WorkerFactory.newInstance(client);

    Worker worker = factory.newWorker(TASK_QUEUE);

    worker.registerWorkflowImplementationTypes(
        CrawlWorkflowImpl.class,
        AnalysisWorkflowImpl.class
    );

    worker.registerActivitiesImplementations(
        mcpActivities,
        crewActivities,
        streamActivities
    );

    factory.start();
    return factory;
  }
}