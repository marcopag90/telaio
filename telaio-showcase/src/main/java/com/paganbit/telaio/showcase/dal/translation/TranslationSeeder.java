package com.paganbit.telaio.showcase.dal.translation;

import com.paganbit.telaio.showcase.seed.AbstractDemoSeeder;
import org.springframework.stereotype.Component;

/**
 * Demo translations — composite-id ({@link TranslationId}) rows.
 */
@Component
class TranslationSeeder extends AbstractDemoSeeder {

    private final TranslationRepository repository;

    TranslationSeeder(TranslationRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    protected void populate() {
        repository.save(translation("greeting", "en", "Hello"));
        repository.save(translation("greeting", "it", "Ciao"));
        repository.save(translation("farewell", "en", "Goodbye"));
    }

    private static Translation translation(String messageKey, String locale, String value) {
        Translation translation = new Translation();
        translation.setId(new TranslationId(messageKey, locale));
        translation.setValue(value);
        return translation;
    }
}
