package com.careerpilot.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "experience")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Experience {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    @Column(name = "employment_type")
    private String employmentType;

    @Column(name = "location_city")
    private String locationCity;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Null while {@code current} is true. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean current;

    /**
     * Free text, typically bullet points. resume-service's rule-based feedback reads the
     * equivalent text from the uploaded CV, not from here -- this copy is what the user
     * curated, which is why it also feeds cover-letter generation.
     */
    @Column(length = 4000)
    private String description;

    public static Experience create(String company, String title, String employmentType,
                                    String locationCity, LocalDate startDate, LocalDate endDate,
                                    boolean current, String description) {
        Experience experience = new Experience();
        experience.id = UUID.randomUUID();
        experience.company = company;
        experience.title = title;
        experience.employmentType = employmentType;
        experience.locationCity = locationCity;
        experience.startDate = startDate;
        // A role cannot be both current and ended; normalise rather than trust the client.
        experience.current = current;
        experience.endDate = current ? null : endDate;
        experience.description = description;
        return experience;
    }
}
