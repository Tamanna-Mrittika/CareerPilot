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
@Table(name = "education")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Education {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false)
    private String institution;

    private String degree;

    @Column(name = "field_of_study")
    private String fieldOfStudy;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** Null means ongoing. */
    @Column(name = "end_date")
    private LocalDate endDate;

    private String grade;

    @Column(length = 2000)
    private String description;

    public static Education create(String institution, String degree, String fieldOfStudy,
                                   LocalDate startDate, LocalDate endDate, String grade,
                                   String description) {
        Education education = new Education();
        education.id = UUID.randomUUID();
        education.institution = institution;
        education.degree = degree;
        education.fieldOfStudy = fieldOfStudy;
        education.startDate = startDate;
        education.endDate = endDate;
        education.grade = grade;
        education.description = description;
        return education;
    }
}
