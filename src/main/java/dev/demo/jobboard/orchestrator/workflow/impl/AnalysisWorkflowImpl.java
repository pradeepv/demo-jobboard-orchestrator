package dev.demo.jobboard.orchestrator.workflow.impl;

import dev.demo.jobboard.orchestrator.workflow.AnalysisWorkflow;
import dev.demo.jobboard.orchestrator.workflow.model.AnalysisRequest;
import dev.demo.jobboard.orchestrator.activity.CrewActivities;
import dev.demo.jobboard.orchestrator.activity.StreamActivities;
import dev.demo.jobboard.orchestrator.activity.NotifyActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class AnalysisWorkflowImpl implements AnalysisWorkflow {

  private final CrewActivities crew = Workflow.newActivityStub(
      CrewActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofMinutes(5))
          .setRetryOptions(RetryOptions.newBuilder()
              .setMaximumAttempts(3)
              .build())
          .build());

  private final StreamActivities stream = Workflow.newActivityStub(
      StreamActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(10))
          .build());

  private final NotifyActivities notify = Workflow.newActivityStub(
      NotifyActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(10))
          .build());

  @Override
  public void start(AnalysisRequest request) {
    var logger = Workflow.getLogger(getClass().getSimpleName());
    String requestId = request.getRequestId();
    List<String> jobIds = request.getJobIds();
    String resumeText = request.getResumeText();
    
    logger.info("Starting analysis for requestId={} ({} jobIds)", requestId, jobIds.size());

    try {
      // Step 1: Send analysis start event
      stream.emit("req:" + requestId, requestId, "analysis", Map.of(
          "kind", "analysisProgress",
          "stage", "starting",
          "message", "Beginning job analysis",
          "totalJobs", jobIds.size()
      ));

      // Step 2: Analyze each job
      double totalScore = 0.0;
      int analyzedCount = 0;
      StringBuilder insights = new StringBuilder();
      
      for (String jobId : jobIds) {
        logger.info("Analyzing job: {}", jobId);
        
        // Mock job data - in real implementation, you'd fetch from database
        String jobTitle = "Software Engineer";
        String company = "Tech Company";
        
        // Analyze the job fit
        CrewActivities.AnalysisResult result = crew.analyze(resumeText, jobId, jobTitle, company);
        
        totalScore += result.getScore();
        analyzedCount++;
        insights.append("Job ").append(jobId).append(": ").append(result.getRationale()).append("\n");
        
        // Send progress update
        stream.emit("req:" + requestId, requestId, "analysis", Map.of(
            "kind", "jobAnalyzed",
            "jobId", jobId,
            "score", result.getScore(),
            "rationale", result.getRationale(),
            "progress", analyzedCount,
            "total", jobIds.size()
        ));
      }

      // Step 3: Generate final recommendations
      double avgScore = totalScore / analyzedCount;
      
      stream.emit("req:" + requestId, requestId, "analysis", Map.of(
          "kind", "generatingResume",
          "stage", "resume_generation",
          "message", "Generating tailored resume and cover letter"
      ));
      
      // Simulate resume/cover letter generation
      String generatedResume = generateResume(resumeText, insights.toString(), avgScore);
      String generatedCoverLetter = generateCoverLetter(resumeText, insights.toString());
      
      // Step 4: Send final results
      stream.emit("req:" + requestId, requestId, "analysis", Map.of(
          "kind", "analysisComplete",
          "stage", "complete",
          "results", Map.of(
              "resume", generatedResume,
              "coverLetter", generatedCoverLetter,
              "atsScore", (int) (avgScore * 100),
              "insights", List.of(
                  "Average match score: " + String.format("%.1f", avgScore * 100) + "%",
                  "Analyzed " + analyzedCount + " job opportunities",
                  "Best matches found in " + jobIds.size() + " positions"
              )
          )
      ));
      
      // Complete the workflow
      notify.completed(requestId);
      logger.info("Analysis completed for requestId={}", requestId);
      
    } catch (Exception e) {
      logger.error("Analysis failed for requestId={}: {}", requestId, e.getMessage(), e);
      
      stream.emit("req:" + requestId, requestId, "analysis", Map.of(
          "kind", "analysisError",
          "stage", "error",
          "error", e.getMessage()
      ));
      
      throw e;
    }
  }
  
  private String generateResume(String originalResume, String insights, double avgScore) {
    return "TAILORED RESUME\n\n" +
           "Based on your profile and job analysis:\n" +
           originalResume + "\n\n" +
           "KEY HIGHLIGHTS FOR TARGET ROLES:\n" +
           "• Match Score: " + String.format("%.0f", avgScore * 100) + "%\n" +
           "• Optimized for ATS systems\n" +
           "• Tailored keywords included\n\n" +
           "INSIGHTS:\n" + insights;
  }
  
  private String generateCoverLetter(String resumeText, String insights) {
    return "TAILORED COVER LETTER\n\n" +
           "Dear Hiring Manager,\n\n" +
           "I am excited to apply for the position. Based on my experience and the role requirements:\n\n" +
           resumeText + "\n\n" +
           "My background aligns well with your needs, as evidenced by our analysis.\n\n" +
           "Best regards,\n" +
           "[Your Name]";
  }
}
