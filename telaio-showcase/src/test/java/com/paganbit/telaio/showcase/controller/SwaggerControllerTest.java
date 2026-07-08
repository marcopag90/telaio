package com.paganbit.telaio.showcase.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwaggerControllerTest {

    private static final SwaggerController controller = new SwaggerController();

    @Test
    void shouldReturnSwaggerRedirectString() {
        assertEquals("redirect:/swagger-ui/index.html", controller.swagger());
    }
}