package io.paganbit.telaio.security.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultDalAccessDeniedMessageResolverTest {

    private DefaultDalAccessDeniedMessageResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultDalAccessDeniedMessageResolver();
    }

    @Test
    void testForCreate() {
        assertEquals("Not authorized to create entity in DAL [testDal]", resolver.forCreate("testDal"));
    }

    @Test
    void testForRead() {
        assertEquals("Not authorized to read entities in DAL [testDal]", resolver.forRead("testDal"));
    }

    @Test
    void testForReadOne() {
        assertEquals("Not authorized to read entity with ID [123] in DAL [testDal]", resolver.forReadOne("testDal", 123L));
    }

    @Test
    void testForUpdate() {
        assertEquals("Not authorized to update entity with ID [123] in DAL [testDal]", resolver.forUpdate("testDal", 123L));
    }

    @Test
    void testForDelete() {
        assertEquals("Not authorized to delete entity with ID [123] in DAL [testDal]", resolver.forDelete("testDal", 123L));
    }
}
