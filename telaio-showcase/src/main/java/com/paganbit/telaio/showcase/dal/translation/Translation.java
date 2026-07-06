package com.paganbit.telaio.showcase.dal.translation;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Use case — a DAL keyed by a <em>composite</em> id ({@link TranslationId}: message key and locale). It
 * exercises the {@code @DalId} Base64 round-trip end to end: the id is a nested object in request bodies
 * and a Base64 URL-safe encoded JSON string in the {@code {id}} path segment.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "translations")
public class Translation {

    @EmbeddedId
    private TranslationId id;

    @NotBlank
    @Column(nullable = false)
    private String value;
}
