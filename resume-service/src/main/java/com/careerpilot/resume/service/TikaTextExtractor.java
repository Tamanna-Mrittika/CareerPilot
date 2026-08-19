package com.careerpilot.resume.service;

import com.careerpilot.common.error.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Extracts raw text from an uploaded PDF via Apache Tika's general-purpose facade.
 *
 * <p>Tika, not PDFBox, does the canonical text extraction that everything downstream
 * (skill matching, section parsing, ATS keyword scoring) reads. PDFBox is reserved for
 * PDF-specific structural analysis Tika's simple API does not expose -- see
 * {@link PdfStructuralAnalyzer}. Splitting the two this way means swapping in support for
 * another resume format later would only touch this class, not the analysis pipeline.
 */
@Component
@Slf4j
public class TikaTextExtractor {

    private final Tika tika = new Tika();

    public String extractText(byte[] pdfBytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(pdfBytes)) {
            String text = tika.parseToString(in);
            return text == null ? "" : text;
        } catch (IOException e) {
            log.warn("Tika text extraction failed", e);
            throw new BadRequestException(
                    "Could not extract text from this PDF. It may be corrupted or password-protected.");
        } catch (org.apache.tika.exception.TikaException e) {
            log.warn("Tika parsing failed", e);
            throw new BadRequestException(
                    "Could not parse this PDF. It may be corrupted, encrypted, or not a valid PDF.");
        }
    }
}
