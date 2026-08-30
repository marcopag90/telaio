package com.paganbit.telaio.showcase.dal.setting;

import com.paganbit.telaio.showcase.seed.AbstractDemoSeeder;
import org.springframework.stereotype.Component;

/**
 * Demo application settings for the internal (non-REST) {@code app-settings} DAL.
 */
@Component
class AppSettingSeeder extends AbstractDemoSeeder {

    private final AppSettingRepository repository;

    AppSettingSeeder(AppSettingRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    protected void populate() {
        repository.save(setting("feature.beta-search.enabled", "false"));
        repository.save(setting("catalog.page-size.default", "20"));
    }

    private static AppSetting setting(String key, String value) {
        AppSetting setting = new AppSetting();
        setting.setId(key);
        setting.setValue(value);
        return setting;
    }
}
