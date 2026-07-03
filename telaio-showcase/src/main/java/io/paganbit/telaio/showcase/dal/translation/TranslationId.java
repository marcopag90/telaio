package io.paganbit.telaio.showcase.dal.translation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/**
 * Composite primary key for {@link Translation}: a message key plus a locale. As a JPA {@code @EmbeddedId}
 * it must be {@link Serializable} and implement {@code equals}/{@code hashCode} (provided by Lombok).
 *
 * <p>Over REST this object is sent as a nested JSON object in the request body, and as a Base64 URL-safe
 * encoded JSON string in the {@code {id}} path segment (decoded by {@code DalIdArgumentResolver}).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class TranslationId implements Serializable {

    @Column(nullable = false)
    private String messageKey;

    @Column(nullable = false)
    private String locale;
}
