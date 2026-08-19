package com.careerpilot.profile.service;

import com.careerpilot.common.error.NotFoundException;
import com.careerpilot.profile.domain.Experience;
import com.careerpilot.profile.domain.Profile;
import com.careerpilot.profile.domain.ProfileSkill;
import com.careerpilot.profile.repository.ProfileRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Compiles a cover letter to PDF on the server, from the stored profile plus the target
 * role.
 *
 * <p>Server-side generation is the point architecturally: the document is produced from
 * data the backend already trusts, so the output cannot be tampered with client-side, and
 * the same endpoint serves the web UI, a future mobile client, or a batch job equally.
 */
@Service
@RequiredArgsConstructor
public class CoverLetterService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private static final Color INK = new Color(0x1a, 0x1a, 0x1a);
    private static final Color MUTED = new Color(0x55, 0x55, 0x55);

    private final ProfileRepository profiles;

    public record CoverLetterRequest(
            String companyName,
            String jobTitle,
            String hiringManager,
            /** Optional free text; when absent a letter is composed from the profile. */
            String customBody) {
    }

    @Transactional(readOnly = true)
    public byte[] generate(UUID userId, CoverLetterRequest request) {
        Profile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No profile exists for this user yet"));

        Document document = new Document(PageSize.A4, 64, 64, 64, 64);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("Cover Letter - " + profile.getFullName());
            document.addAuthor(profile.getFullName());
            document.open();

            writeSenderBlock(document, profile);
            writeDate(document);
            writeRecipientBlock(document, request);
            writeSalutation(document, request);
            writeBody(document, profile, request);
            writeSignOff(document, profile);

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to compile cover letter PDF", e);
        }

        return out.toByteArray();
    }

    private void writeSenderBlock(Document document, Profile profile) throws DocumentException {
        Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, INK);
        Paragraph name = new Paragraph(profile.getFullName(), nameFont);
        name.setSpacingAfter(2f);
        document.add(name);

        String contact = joinNonBlank(" · ",
                profile.getEmail(), profile.getPhone(),
                joinNonBlank(", ", profile.getLocationCity(), profile.getLocationCountry()));
        if (StringUtils.hasText(contact)) {
            Paragraph p = new Paragraph(contact, FontFactory.getFont(FontFactory.HELVETICA, 9.5f, MUTED));
            p.setSpacingAfter(2f);
            document.add(p);
        }

        String links = joinNonBlank(" · ",
                profile.getLinkedinUrl(), profile.getGithubUrl(), profile.getPortfolioUrl());
        if (StringUtils.hasText(links)) {
            Paragraph p = new Paragraph(links, FontFactory.getFont(FontFactory.HELVETICA, 9.5f, MUTED));
            p.setSpacingAfter(22f);
            document.add(p);
        }
    }

    private void writeDate(Document document) throws DocumentException {
        Paragraph date = new Paragraph(LocalDate.now().format(DATE),
                FontFactory.getFont(FontFactory.HELVETICA, 10.5f, MUTED));
        date.setSpacingAfter(18f);
        document.add(date);
    }

    private void writeRecipientBlock(Document document, CoverLetterRequest request) throws DocumentException {
        if (!StringUtils.hasText(request.companyName())) {
            return;
        }
        Paragraph company = new Paragraph(request.companyName(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, INK));
        company.setSpacingAfter(18f);
        document.add(company);
    }

    private void writeSalutation(Document document, CoverLetterRequest request) throws DocumentException {
        // "Dear Hiring Manager" is the correct fallback: inventing a name would be worse
        // than being generic, and "To Whom It May Concern" reads as boilerplate.
        String greeting = StringUtils.hasText(request.hiringManager())
                ? "Dear " + request.hiringManager() + ","
                : "Dear Hiring Manager,";
        Paragraph p = new Paragraph(greeting, FontFactory.getFont(FontFactory.HELVETICA, 11, INK));
        p.setSpacingAfter(12f);
        document.add(p);
    }

    private void writeBody(Document document, Profile profile, CoverLetterRequest request)
            throws DocumentException {
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11, INK);

        String body = StringUtils.hasText(request.customBody())
                ? request.customBody()
                : composeBody(profile, request);

        for (String block : body.split("\n\n")) {
            if (block.isBlank()) {
                continue;
            }
            Paragraph p = new Paragraph(block.trim(), bodyFont);
            p.setAlignment(Element.ALIGN_JUSTIFIED);
            p.setLeading(16f);
            p.setSpacingAfter(11f);
            document.add(p);
        }
    }

    /**
     * Composes a letter from profile data when the user has not written their own.
     *
     * <p>Template-driven and deliberately modest: it states real facts drawn from the
     * profile rather than inventing enthusiasm. A generated letter that overclaims is
     * worse than a short honest one, and the user can always override with customBody.
     */
    private String composeBody(Profile profile, CoverLetterRequest request) {
        String role = StringUtils.hasText(request.jobTitle()) ? request.jobTitle() : "the advertised role";
        String company = StringUtils.hasText(request.companyName()) ? request.companyName() : "your organisation";

        StringBuilder body = new StringBuilder();

        body.append("I am writing to apply for ").append(role).append(" at ").append(company).append(". ");
        if (profile.getYearsExperience() != null && profile.getYearsExperience() > 0) {
            body.append("I bring ").append(profile.getYearsExperience())
                    .append(profile.getYearsExperience() == 1 ? " year" : " years")
                    .append(" of professional experience");
            if (StringUtils.hasText(profile.getHeadline())) {
                body.append(" as ").append(profile.getHeadline().toLowerCase(Locale.ENGLISH));
            }
            body.append(".");
        } else if (StringUtils.hasText(profile.getHeadline())) {
            body.append("I work as ").append(profile.getHeadline().toLowerCase(Locale.ENGLISH)).append(".");
        }
        body.append("\n\n");

        if (StringUtils.hasText(profile.getSummary())) {
            body.append(profile.getSummary().trim()).append("\n\n");
        }

        // "Most recently" means the role held most recently, so rank by end date (a current
        // role counts as today) and only fall back to start date to break ties. Ranking by
        // start date alone picks a short 2023 contract over a job actually held until 2024.
        profile.getExperience().stream()
                .max(Comparator.comparing((Experience e) ->
                                e.isCurrent() || e.getEndDate() == null
                                        ? LocalDate.now()
                                        : e.getEndDate())
                        .thenComparing(Experience::getStartDate,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .ifPresent(recent -> {
                    body.append("Most recently, I worked as ").append(recent.getTitle())
                            .append(" at ").append(recent.getCompany());
                    if (recent.getStartDate() != null) {
                        body.append(" (").append(recent.getStartDate().format(MONTH_YEAR))
                                .append(recent.isCurrent() ? " – present" : "")
                                .append(")");
                    }
                    body.append(". ");
                    if (StringUtils.hasText(recent.getDescription())) {
                        body.append(firstSentence(recent.getDescription()));
                    }
                    body.append("\n\n");
                });

        List<String> topSkills = profile.getSkills().stream()
                .sorted(Comparator.comparingDouble(
                        (ProfileSkill ps) -> ps.getProficiency().weight()).reversed())
                .limit(6)
                .map(ps -> ps.getSkill().getName())
                .collect(Collectors.toList());

        if (!topSkills.isEmpty()) {
            body.append("My core technical strengths include ")
                    .append(joinWithAnd(topSkills)).append(". ");
            body.append("I would welcome the opportunity to discuss how this experience fits ")
                    .append(company).append("'s needs.\n\n");
        }

        return body.toString();
    }

    private void writeSignOff(Document document, Profile profile) throws DocumentException {
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11, INK);
        Paragraph closing = new Paragraph("Yours sincerely,", bodyFont);
        closing.setSpacingBefore(6f);
        closing.setSpacingAfter(28f);
        document.add(closing);

        document.add(new Paragraph(profile.getFullName(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, INK)));
    }

    private static String firstSentence(String text) {
        String trimmed = text.trim();
        int end = trimmed.indexOf(". ");
        return end > 0 ? trimmed.substring(0, end + 1) : trimmed;
    }

    private static String joinWithAnd(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1))
                + " and " + items.get(items.size() - 1);
    }

    private static String joinNonBlank(String separator, String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(separator));
    }
}
