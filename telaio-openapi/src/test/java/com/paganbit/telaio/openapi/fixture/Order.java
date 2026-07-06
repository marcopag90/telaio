package com.paganbit.telaio.openapi.fixture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Test entity mirroring the showcase {@code Employee}/{@code department} shape: a read-only relation
 * (a {@code $ref} property, the case swagger-core drops {@code readOnly} on) plus a write-only foreign key.
 */
@Getter
@Setter
@SuppressWarnings("unused")
public class Order {

    private Long id;

    /**
     * Read-only relation: present in responses, must be absent from the request example.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Customer customer;

    /**
     * Write-only foreign key: present in requests, must be absent from responses.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long customerId;

    private String code;
}
