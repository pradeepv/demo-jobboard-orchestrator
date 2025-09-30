package dev.demo.jobboard.orchestrator.activity.impl;

import static java.util.Map.entry;

import dev.demo.jobboard.orchestrator.activity.CrewActivities;
import dev.demo.jobboard.orchestrator.sse.SseEventBus;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrewActivitiesImpl implements CrewActivities {

  private final SseEventBus bus;

  private static final Logger log = LoggerFactory.getLogger(
    CrewActivitiesImpl.class
  );

  public CrewActivitiesImpl(SseEventBus bus) {
    this.bus = bus;
  }

  // Keywords and their weights for different categories
  private static final Map<String, Double> TECHNICAL_KEYWORDS = Map.ofEntries(
    entry("java", 0.15),
    entry("python", 0.15),
    entry("javascript", 0.12),
    entry("typescript", 0.12),
    entry("react", 0.10),
    entry("node", 0.10),
    entry("spring", 0.08),
    entry("aws", 0.08),
    entry("docker", 0.06),
    entry("kubernetes", 0.06),
    entry("sql", 0.05),
    entry("nosql", 0.05)
  );

  private static final Map<String, Double> EXPERIENCE_KEYWORDS = Map.ofEntries(
    entry("senior", 0.15),
    entry("lead", 0.12),
    entry("architect", 0.10),
    entry("principal", 0.10),
    entry("backend", 0.08),
    entry("frontend", 0.08),
    entry("fullstack", 0.08),
    entry("devops", 0.06),
    entry("microservices", 0.05),
    entry("api", 0.05),
    entry("database", 0.05),
    entry("cloud", 0.05)
  );

  private static final Map<String, Double> SOFT_SKILLS = Map.ofEntries(
    entry("team", 0.06),
    entry("leadership", 0.08),
    entry("communication", 0.05),
    entry("agile", 0.04),
    entry("scrum", 0.04),
    entry("collaboration", 0.04),
    entry("problem solving", 0.05),
    entry("mentoring", 0.06)
  );

  @Override
  public AnalysisResult analyze(
    String resumeText,
    String jobId,
    String jobTitle,
    String company
  ) {
    log.info("Analyzing job {} - {} @ {}", jobId, jobTitle, company);

    if (resumeText == null || resumeText.trim().isEmpty()) {
      return new AnalysisResult(0.1, "No resume text provided for analysis");
    }

    String resumeLower = resumeText.toLowerCase();
    String jobTitleLower = jobTitle.toLowerCase();

    // Calculate scores for different categories
    double techScore = calculateKeywordScore(resumeLower, TECHNICAL_KEYWORDS);
    double expScore = calculateKeywordScore(resumeLower, EXPERIENCE_KEYWORDS);
    double softScore = calculateKeywordScore(resumeLower, SOFT_SKILLS);

    // Job title relevance boost
    double titleRelevance = calculateTitleRelevance(resumeLower, jobTitleLower);

    // Company size/type adjustment (mock logic)
    double companyFit = calculateCompanyFit(company.toLowerCase());

    // Weighted total score
    double totalScore =
      (techScore * 0.4) +
      (expScore * 0.3) +
      (softScore * 0.15) +
      (titleRelevance * 0.10) +
      (companyFit * 0.05);

    // Ensure score is between 0 and 1
    totalScore = Math.max(0.0, Math.min(1.0, totalScore));

    // Generate detailed rationale
    String rationale = buildRationale(
      jobTitle,
      company,
      techScore,
      expScore,
      softScore,
      titleRelevance,
      companyFit,
      totalScore
    );

    log.debug(
      "Analysis complete for job {}: score={}, rationale={}",
      jobId,
      totalScore,
      rationale
    );
    return new AnalysisResult(totalScore, rationale);
  }

  private double calculateKeywordScore(
    String text,
    Map<String, Double> keywords
  ) {
    double score = 0.0;
    for (Map.Entry<String, Double> entry : keywords.entrySet()) {
      if (text.contains(entry.getKey())) {
        score += entry.getValue();
      }
    }
    return Math.min(score, 1.0); // Cap at 1.0
  }

  private double calculateTitleRelevance(String resumeText, String jobTitle) {
    // Simple relevance calculation based on common job title keywords
    double relevance = 0.0;

    if (
      jobTitle.contains("engineer") && resumeText.contains("engineer")
    ) relevance += 0.3;
    if (
      jobTitle.contains("developer") && resumeText.contains("develop")
    ) relevance += 0.3;
    if (
      jobTitle.contains("senior") && resumeText.contains("senior")
    ) relevance += 0.2;
    if (jobTitle.contains("lead") && resumeText.contains("lead")) relevance +=
      0.2;
    if (
      jobTitle.contains("backend") && resumeText.contains("backend")
    ) relevance += 0.2;
    if (
      jobTitle.contains("frontend") && resumeText.contains("frontend")
    ) relevance += 0.2;
    if (
      jobTitle.contains("full") && resumeText.contains("fullstack")
    ) relevance += 0.2;

    return Math.min(relevance, 1.0);
  }

  private double calculateCompanyFit(String company) {
    // Mock company fit calculation based on company characteristics
    double fit = 0.5; // Base fit

    if (company.contains("startup") || company.contains("tech")) fit += 0.2;
    if (company.contains("enterprise") || company.contains("corp")) fit += 0.1;
    if (company.contains("remote") || company.contains("distributed")) fit +=
      0.15;

    return Math.min(fit, 1.0);
  }

  private String buildRationale(
    String jobTitle,
    String company,
    double techScore,
    double expScore,
    double softScore,
    double titleRelevance,
    double companyFit,
    double totalScore
  ) {
    StringBuilder rationale = new StringBuilder();

    rationale.append(
      String.format("Analysis for %s @ %s:\n", jobTitle, company)
    );
    rationale.append(
      String.format("• Technical Skills Match: %.1f%%\n", techScore * 100)
    );
    rationale.append(
      String.format("• Experience Relevance: %.1f%%\n", expScore * 100)
    );
    rationale.append(
      String.format("• Soft Skills Alignment: %.1f%%\n", softScore * 100)
    );
    rationale.append(
      String.format("• Job Title Relevance: %.1f%%\n", titleRelevance * 100)
    );
    rationale.append(
      String.format("• Company Culture Fit: %.1f%%\n", companyFit * 100)
    );

    rationale.append("\nRecommendations: ");
    if (totalScore >= 0.8) {
      rationale.append("Excellent match! Strongly recommended to apply.");
    } else if (totalScore >= 0.6) {
      rationale.append("Good match with room for growth. Consider applying.");
    } else if (totalScore >= 0.4) {
      rationale.append("Moderate match. May require skill development.");
    } else {
      rationale.append(
        "Lower match. Consider gaining more relevant experience."
      );
    }

    return rationale.toString();
  }
}
