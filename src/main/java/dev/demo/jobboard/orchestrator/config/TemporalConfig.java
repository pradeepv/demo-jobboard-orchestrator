package dev.demo.jobboard.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.demo.jobboard.orchestrator.activity.impl.CrewActivitiesImpl;
import dev.demo.jobboard.orchestrator.activity.impl.McpActivitiesImpl;
import dev.demo.jobboard.orchestrator.activity.impl.StreamActivitiesImpl;
import dev.demo.jobboard.orchestrator.workflow.impl.AnalysisWorkflowImpl;
import dev.demo.jobboard.orchestrator.workflow.impl.CrawlWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

@Configuration
public class TemporalConfig {

    public static final String TASK_QUEUE = "demo-tq";

    @Value("${temporal.server:127.0.0.1:7233}")
    private String temporalServer;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(
            serviceStubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace("default")
                .build()
        );
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
      WorkerFactory factory = WorkerFactory.newInstance(client);
      Worker worker = factory.newWorker(TASK_QUEUE);
  
      // Register workflows
      worker.registerWorkflowImplementationTypes(
          CrawlWorkflowImpl.class,
          AnalysisWorkflowImpl.class
      );
  
      // Register activities (stubbed implementations)
      worker.registerActivitiesImplementations(
          new McpActivitiesImpl(),
          new CrewActivitiesImpl(),
          new StreamActivitiesImpl()
      );
  
      factory.start();
      return factory;
    }
}