# System Architecture and User Flow

This document outlines the architecture of the job board orchestrator system and the user flow from a user's perspective.

## System Architecture

The system is composed of the following components:

1.  **API:** The API is the entry point for the user. It provides endpoints for starting crawls and analyses, and for streaming the results back to the user.
2.  **Temporal:** Temporal is used to orchestrate the crawling and analysis workflows.
3.  **Workers:** The workers are responsible for executing the activities that are part of the workflows.
4.  **MCP (Multi-Content Platform):** The MCP is a Python script that is used to scrape job postings from various sources.
5.  **SSE (Server-Sent Events):** SSE is used to stream the progress of the crawls and analyses back to the user in real-time.

## Temporal Workflows and Activities

Temporal is at the heart of the system's orchestration. It provides a reliable and scalable way to execute long-running and complex workflows. The system uses two main workflows: `CrawlWorkflow` and `AnalysisWorkflow`.

### CrawlWorkflow

The `CrawlWorkflow` is responsible for crawling job postings from a given source. It is initiated by the `WorkflowController` (although the current implementation uses a local script runner, a Temporal workflow is designed for this). The workflow orchestrates the following activities:

-   **`McpActivities.fetchPage`:** This activity calls the MCP script to fetch a page of job postings.
-   **`StreamActivities.emit`:** This activity sends the fetched job postings to the user via an SSE stream.
-   **`NotifyActivities.sourceComplete`:** This activity notifies the API when the crawl is complete.

### AnalysisWorkflow

The `AnalysisWorkflow` is responsible for analyzing job postings against a user's resume. It is initiated by the `WorkflowController` when the user sends a POST request to the `/api/analysis` endpoint. The workflow orchestrates the following activities:

-   **`CrewActivities.analyze`:** This activity analyzes a single job posting against the user's resume and returns a match score and rationale.
-   **`StreamActivities.emitObj`:** This activity sends the analysis progress and results to the user via an SSE stream.
-   **`NotifyActivities.sourceComplete`:** This activity notifies the API when the analysis is complete.

The activities are implemented in the `dev.demo.jobboard.orchestrator.activity.impl` package. They are responsible for the actual work of the system, such as calling the MCP script, analyzing job postings, and sending SSE events.

## User Flow

The following steps outline the user flow for crawling and analyzing job postings:

### 1. Crawling Job Postings

The user can initiate a crawl of job postings by sending a POST request to the `/api/local/crawl` endpoint. This endpoint is handled by the `McpRunnerController`.

**Request:**

```
POST /api/local/crawl
?sources=yc,hn
&keywords=backend%20engineer
&location=remote
&remoteOnly=true
&maxPages=2
&perSourceLimit=10
```

**Response:**

```json
{
    "runId": "run-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "sseUrl": "/api/local/stream/run-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

The `McpRunnerController` starts a Python script (MCP) to scrape job postings from the specified sources. The controller returns a `runId` and an SSE URL that the user can use to monitor the progress of the crawl.

The user can connect to the SSE stream to receive real-time updates on the crawl. The stream will contain events for each page that is crawled, as well as the job postings that are found.

### 2. Analyzing Job Postings

Once the crawl is complete, the user can initiate an analysis of the crawled job postings by sending a POST request to the `/api/analysis` endpoint. This endpoint is handled by the `WorkflowController`.

**Request:**

```json
POST /api/analysis
{
    "jobIds": ["job-id-1", "job-id-2", "job-id-3"],
    "resumeText": "This is my resume..."
}
```

**Response:**

```json
{
    "status": "started",
    "requestId": "analysis-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "sseUrl": "/api/stream/analysis-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

The `WorkflowController` starts an `AnalysisWorkflow` in Temporal. The workflow takes a list of `jobIds` and the user's resume as input. The controller returns a `requestId` and an SSE URL that the user can use to monitor the progress of the analysis.

The `AnalysisWorkflow` orchestrates the following steps:

1.  For each `jobId`, it calls the `analyze` activity to analyze the job fit.
2.  The `analyze` activity uses a keyword-based approach to compare the user's resume with the job description and calculate a match score.
3.  The workflow then generates a tailored resume and cover letter based on the analysis results.
4.  The workflow sends progress updates and the final results to the user via the SSE stream.

The user can connect to the SSE stream to receive real-time updates on the analysis. The stream will contain events for each job that is analyzed, as well as the final analysis results, including the tailored resume and cover letter.

## Conclusion

This document provides a high-level overview of the system architecture and user flow. The system is designed to be a flexible and scalable platform for crawling and analyzing job postings. The use of Temporal for orchestration and SSE for real-time updates provides a robust and user-friendly experience.