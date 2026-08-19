package com.careerpilot.resume.service;

import com.careerpilot.common.error.BadRequestException;
import com.careerpilot.resume.domain.AtsCheckType;
import com.careerpilot.resume.domain.Severity;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Structural checks that catch what actually breaks real ATS parsers, using PDFBox's
 * document model rather than a full layout-analysis engine.
 *
 * <p>Deliberately scoped to four checks (see {@link AtsCheckType}): page count, a
 * scanned-image detector (very low extractable text relative to page count -- the single
 * most common real-world ATS failure, since a scanned resume has no text layer at all),
 * embedded images, and missing contact information. A genuine multi-column layout detector
 * would need geometric clustering of text-run coordinates; the return on that complexity
 * is small next to what these four checks already catch.
 */
@Component
@Slf4j
public class PdfStructuralAnalyzer {

    private static final int MIN_CHARS_PER_PAGE_FOR_REAL_TEXT = 100;
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile(
            "(\\+?\\d[\\d\\-.\\s()]{7,}\\d)");

    public record Finding(AtsCheckType type, Severity severity, String message) {
    }

    public List<Finding> analyze(byte[] pdfBytes, String extractedText) {
        List<Finding> findings = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            findings.add(pageCountFinding(pageCount));
            findings.addAll(scannedImageFinding(pageCount, extractedText));
            findings.addAll(embeddedImageFinding(document));
        } catch (IOException e) {
            log.warn("PDFBox failed to load the document for structural analysis", e);
            throw new BadRequestException(
                    "Could not open this PDF for analysis. It may be corrupted or password-protected.");
        }

        findings.addAll(contactInfoFinding(extractedText));
        return findings;
    }

    private Finding pageCountFinding(int pageCount) {
        Severity severity = pageCount <= 2 ? Severity.INFO
                : pageCount == 3 ? Severity.WARNING
                : Severity.CRITICAL;
        String message = pageCount <= 2
                ? pageCount + " page(s) -- a good length for most ATS and recruiter screens."
                : pageCount + " pages -- most ATS-friendly resumes are 1-2 pages; consider trimming.";
        return new Finding(AtsCheckType.PAGE_COUNT, severity, message);
    }

    /**
     * A page with almost no extractable text is almost always a scanned image with no real
     * text layer -- the single most damaging thing a resume can do to its ATS chances,
     * since the parser sees nothing at all, not even garbled text.
     */
    private List<Finding> scannedImageFinding(int pageCount, String extractedText) {
        int textLength = extractedText == null ? 0 : extractedText.strip().length();
        double charsPerPage = pageCount == 0 ? 0 : (double) textLength / pageCount;

        if (charsPerPage < MIN_CHARS_PER_PAGE_FOR_REAL_TEXT) {
            return List.of(new Finding(AtsCheckType.SCANNED_IMAGE, Severity.CRITICAL,
                    "This PDF appears to contain little or no extractable text -- likely a "
                            + "scanned image. ATS systems cannot read scanned resumes at all; "
                            + "export a text-based PDF instead."));
        }
        return List.of();
    }

    private List<Finding> embeddedImageFinding(PDDocument document) throws IOException {
        int imageCount = 0;
        for (PDPage page : document.getPages()) {
            for (org.apache.pdfbox.cos.COSName xObjectName : page.getResources().getXObjectNames()) {
                PDXObject xObject = page.getResources().getXObject(xObjectName);
                if (xObject instanceof PDImageXObject) {
                    imageCount++;
                }
            }
        }

        if (imageCount == 0) {
            return List.of();
        }
        return List.of(new Finding(AtsCheckType.EMBEDDED_IMAGES, Severity.WARNING,
                imageCount + " embedded image(s) found (a photo, icons, or a logo). Some "
                        + "ATS systems cannot process image content, and any text baked "
                        + "into an image is invisible to them."));
    }

    private List<Finding> contactInfoFinding(String extractedText) {
        String text = extractedText == null ? "" : extractedText;
        boolean hasEmail = EMAIL.matcher(text).find();
        boolean hasPhone = PHONE.matcher(text).find();

        if (!hasEmail && !hasPhone) {
            return List.of(new Finding(AtsCheckType.MISSING_CONTACT_INFO, Severity.CRITICAL,
                    "No email address or phone number was detected. Recruiters and ATS "
                            + "systems that extract contact details will not be able to reach you."));
        }
        if (!hasEmail) {
            return List.of(new Finding(AtsCheckType.MISSING_CONTACT_INFO, Severity.WARNING,
                    "No email address was detected in the extracted text."));
        }
        if (!hasPhone) {
            return List.of(new Finding(AtsCheckType.MISSING_CONTACT_INFO, Severity.WARNING,
                    "No phone number was detected in the extracted text."));
        }
        return List.of();
    }
}
