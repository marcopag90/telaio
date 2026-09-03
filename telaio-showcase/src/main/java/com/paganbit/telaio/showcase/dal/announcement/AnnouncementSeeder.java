package com.paganbit.telaio.showcase.dal.announcement;

import com.paganbit.telaio.showcase.seed.AbstractDemoSeeder;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Demo announcements, one per {@link AnnouncementType}.
 */
@Component
class AnnouncementSeeder extends AbstractDemoSeeder {

    private final AnnouncementRepository repository;

    AnnouncementSeeder(AnnouncementRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    protected void populate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        repository.save(announcement("Scheduled Maintenance Window",
            "The system will undergo scheduled maintenance on Saturday from 02:00 to 04:00 UTC. "
                + "All services will be unavailable during this window.",
            AnnouncementType.INFO, now.minusDays(1), now.plusDays(5)));
        repository.save(announcement("Deprecation Notice: Legacy API v0",
            "The legacy API v0 endpoints will be removed on December 31st. "
                + "Please migrate to the v1 API as soon as possible.",
            AnnouncementType.WARNING, now.minusDays(7), now.plusMonths(3)));
        repository.save(announcement("Critical Security Patch Applied",
            "A critical security vulnerability has been patched in the authentication module. "
                + "All users are required to re-authenticate.",
            AnnouncementType.CRITICAL, now.minusHours(2), null));
    }

    @SuppressWarnings("squid:S4449")
    private static Announcement announcement(
        String title, String message, AnnouncementType type,
        LocalDateTime publishedAt, @Nullable LocalDateTime expiresAt
    ) {
        Announcement announcement = new Announcement();
        announcement.setTitle(title);
        announcement.setMessage(message);
        announcement.setType(type);
        announcement.setPublishedAt(publishedAt);
        announcement.setExpiresAt(expiresAt);
        return announcement;
    }
}
