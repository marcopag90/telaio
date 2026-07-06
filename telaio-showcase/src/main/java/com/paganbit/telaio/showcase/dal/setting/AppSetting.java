package com.paganbit.telaio.showcase.dal.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An internal application setting: a simple key/value pair the application reads at runtime
 * (feature flags, tuning knobs, operational toggles).
 *
 * <p>It is managed through {@code AppSettingDalService}, an <em>internal</em> DAL: settings are an
 * implementation detail of the running application, never a public REST resource — so the DAL keeps all
 * the framework conveniences (validation, transactions, metrics, audit) while staying off the web
 * boundary entirely.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_settings")
public class AppSetting {

    /**
     * The setting key, assigned by the caller (e.g. {@code "feature.beta-search.enabled"}).
     */
    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @NotBlank
    @Column(nullable = false)
    private String value;
}
