package com.paganbit.telaio.showcase.dal.notification;

import com.paganbit.telaio.showcase.seed.AbstractDemoSeeder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Demo notifications, one per {@link NotificationChannel}, stored in MongoDB.
 */
@Component
class NotificationSeeder extends AbstractDemoSeeder {

    private final NotificationRepository repository;

    NotificationSeeder(NotificationRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    protected void populate() {
        repository.save(notification("ada@example.com", "Welcome aboard",
            "Your account on the Telaio showcase is ready.", NotificationChannel.EMAIL));
        repository.save(notification("+39 333 0000000", "Ticket update",
            "Your support ticket has been updated.", NotificationChannel.SMS));
        repository.save(notification("https://hooks.example.com/telaio", "Nightly build",
            "The nightly build passed.", NotificationChannel.WEBHOOK));
    }

    private static Notification notification(
        String recipient, String subject, String message, NotificationChannel channel
    ) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setSubject(subject);
        notification.setMessage(message);
        notification.setChannel(channel);
        notification.setCreatedAt(Instant.now());
        return notification;
    }
}
