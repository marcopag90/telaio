package com.paganbit.telaio.showcase.dal.setting;

import com.paganbit.telaio.jpa.JpaDalRepository;

/**
 * DAL repository backing {@code AppSettingDalService}. Unlike {@code ProductPriceHistoryRepository}
 * (a plain {@code JpaRepository}), this is a {@link JpaDalRepository} because settings are driven
 * <em>through</em> a {@code @DalService} — albeit an internal one.
 */
public interface AppSettingRepository extends JpaDalRepository<AppSetting, String> {
}
