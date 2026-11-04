package com.paganbit.telaio.openapi.fixture;

import lombok.Getter;
import lombok.Setter;

/**
 * Test entity for a DAL that is read-only on REST ({@code @DalWeb(operations = {READ, READ_ONE})}).
 */
@Setter
@Getter
@SuppressWarnings("unused")
public class Archive {

    private Long id;
    private String label;
}
