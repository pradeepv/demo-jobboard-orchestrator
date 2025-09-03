package dev.demo.jobboard.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            // Allow localhost and LAN hosts on port 3000
            .allowedOriginPatterns(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "http://192.168.*.*:3000",
                "http://10.*.*.*:3000"
            )
            .allowedMethods("GET","POST","OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Content-Type")
            .allowCredentials(true)
            .maxAge(3600);
      }
    };
  }
}