package com.paganbit.telaio.showcase.dal.bulletin;

import com.paganbit.telaio.showcase.seed.AbstractDemoSeeder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Demo bulletin — readable by everyone, writable by ADMIN only ({@code AdminWritesDalAuthAdapter}).
 */
@Component
class BulletinSeeder extends AbstractDemoSeeder {

    private final BulletinRepository repository;

    BulletinSeeder(BulletinRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    protected void populate() {
        Bulletin welcome = new Bulletin();
        welcome.setTitle("Welcome to the Telaio showcase");
        welcome.setMessage("Everyone can read bulletins; only ADMIN can post, edit or delete them.");
        welcome.setPostedAt(LocalDateTime.now(ZoneId.systemDefault()).minusDays(2));
        repository.save(welcome);
    }
}
