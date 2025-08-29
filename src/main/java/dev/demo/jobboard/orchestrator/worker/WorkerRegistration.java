package dev.demo.jobboard.orchestrator.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.demo.jobboard.orchestrator.activity.impl.PaymentActivitiesImpl;
import dev.demo.jobboard.orchestrator.workflow.impl.PaymentWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

@Configuration
public class WorkerRegistration {

    @Bean
    public Worker paymentWorker(
            WorkerFactory workerFactory,
            PaymentActivitiesImpl activitiesImpl
    ) {
        Worker worker = workerFactory.newWorker("PAYMENT_TASK_QUEUE");
        worker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
        worker.registerActivitiesImplementations(activitiesImpl);
        workerFactory.start();
        return worker;
    }
}