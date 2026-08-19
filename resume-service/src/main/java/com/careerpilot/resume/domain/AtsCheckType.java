package com.careerpilot.resume.domain;

/**
 * Structural PDF properties that break real ATS (Applicant Tracking System) parsers.
 *
 * <p>Deliberately scoped to checks that are cheap and reliable to compute from
 * {@code PDFBox} without a full layout-analysis engine: page count, whether the PDF is
 * actually a scanned image with no extractable text, embedded images, and whether contact
 * information is present in the extracted text at all. A true multi-column layout detector
 * would need real geometric text-run clustering -- out of scope for the return it offers
 * over these four checks, which catch the large majority of real ATS failures.
 */
public enum AtsCheckType {
    PAGE_COUNT,
    SCANNED_IMAGE,
    EMBEDDED_IMAGES,
    MISSING_CONTACT_INFO
}
