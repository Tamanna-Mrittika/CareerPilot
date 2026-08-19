package com.careerpilot.resume.domain;

/**
 * Lifecycle of one uploaded resume. This is the async story without a message broker: the
 * upload request returns 202 with the resume in {@code PENDING}, a bounded {@code @Async}
 * worker moves it through {@code PROCESSING} to {@code COMPLETED}/{@code FAILED}, and the
 * client polls {@code GET /api/v1/resumes/{id}} to observe the transition.
 */
public enum ResumeStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
