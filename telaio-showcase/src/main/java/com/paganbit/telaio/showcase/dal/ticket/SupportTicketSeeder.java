package com.paganbit.telaio.showcase.dal.ticket;

import com.paganbit.telaio.showcase.seed.AbstractDemoSeeder;
import org.springframework.stereotype.Component;

/**
 * Demo support ticket. Written through the repository, not the DAL, so no feed activity is published
 * at startup.
 */
@Component
class SupportTicketSeeder extends AbstractDemoSeeder {

    private final SupportTicketRepository repository;

    SupportTicketSeeder(SupportTicketRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    protected void populate() {
        SupportTicket ticket = new SupportTicket();
        ticket.setSubject("Onboarding: request VPN access");
        ticket.setStatus("OPEN");
        repository.save(ticket);
    }
}
