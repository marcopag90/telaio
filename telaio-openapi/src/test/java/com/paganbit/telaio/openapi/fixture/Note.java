package com.paganbit.telaio.openapi.fixture;

import lombok.Getter;
import lombok.Setter;

/**
 * Test entity for a DAL keyed by a simple {@code String} identifier.
 */
@Setter
@Getter
@SuppressWarnings("unused")
public class Note {

    private String id;
    private String text;
}
