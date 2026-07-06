package com.paganbit.telaio.openapi.fixture;

import lombok.Getter;
import lombok.Setter;

/**
 * Test entity used as the target of a relation, so it resolves to a {@code $ref} schema.
 */
@Getter
@Setter
@SuppressWarnings("unused")
public class Customer {

    private Long id;
    private String name;
}
