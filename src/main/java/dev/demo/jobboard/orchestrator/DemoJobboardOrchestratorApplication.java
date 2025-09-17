package dev.demo.jobboard.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DemoJobboardOrchestratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoJobboardOrchestratorApplication.class, args);
	}

}
