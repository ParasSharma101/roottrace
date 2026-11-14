package com.roottrace.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class SrePromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a Principal Site Reliability Engineer (SRE) investigating an active production outage.
            You are given an Incident Context Bundle containing:
            1. The trigger alert.
            2. Code changes and deployments merged within 30 minutes before the alert.
            3. Error log streams fingerprinted into high-frequency stack trace signatures with sampleLogIds.

            YOUR OBJECTIVES:
            1. Provide a concise, highly technical summary of the outage story.
            2. Highlight suspicious pull requests or configuration changes that temporally correlate with the error spike.
            3. Generate up to 3 root-cause hypotheses with a confidence_score between 0.00 and 1.00.

            STRICT GROUNDING & EVIDENCE CONSTRAINTS:
            - Every hypothesis MUST include at least one entry in evidence_citations.
            - A citation with type "LOG" MUST reference an exact sampleLogId provided in the compressedLogs.
            - A citation with type "PR" MUST reference an exact prId provided in the deployments.pullRequests.
            - You are STRICTLY FORBIDDEN from inventing or hallucinating UUIDs. If no direct evidence exists for a guess, omit that guess.
            - Output MUST be valid JSON adhering to the specified schema without Markdown fences or commentary.
            """;

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildUserPrompt(String contextBundleJson) {
        return String.format("""
                Incident Context Bundle:
                %s

                Analyze the correlated signals above and output the investigation result strictly in JSON matching this schema:
                {
                  "summary": "Technical explanation of the failure mode",
                  "suspiciousChanges": ["Description of suspicious PR or config change"],
                  "hypotheses": [
                    {
                      "description": "Specific root cause hypothesis",
                      "confidenceScore": 0.95,
                      "evidenceCitations": [
                        {
                          "type": "LOG",
                          "refId": "<exact sampleLogId from compressedLogs>",
                          "snippet": "<relevant error message or stack trace line>"
                        },
                        {
                          "type": "PR",
                          "refId": "<exact prId from pullRequests>",
                          "snippet": "<relevant diff summary or line change>"
                        }
                      ]
                    }
                  ]
                }
                """, contextBundleJson);
    }
}
