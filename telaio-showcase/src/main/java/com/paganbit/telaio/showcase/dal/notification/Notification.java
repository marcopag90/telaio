package com.paganbit.telaio.showcase.dal.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A notification stored in MongoDB — the showcase's document-store entity. The {@code String} id is
 * mapped onto {@code _id} (Spring Data generates an ObjectId hex string when unset) and travels
 * unchanged in the {@code /dal/v1/notifications/{id}} URL. {@link Version} enables optimistic
 * locking, including the version-checked delete.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    @NotBlank
    private String recipient;

    @NotBlank
    private String subject;

    @NotBlank
    private String message;

    @NotNull
    private NotificationChannel channel;

    @Version
    private Long version;
}
