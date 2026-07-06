package com.paganbit.telaio.security.adapter;

import com.paganbit.telaio.core.adapter.DalOperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class NoopDalRbacAdapterTest {

    @Mock
    private Authentication mockAuthentication;

    private NoopDalRbacAdapter<TestDto> adapter;
    private Map<String, Object> testInput;
    private TestDto testDto;

    @BeforeEach
    void setUp() {
        adapter = new NoopDalRbacAdapter<>();
        testInput = new HashMap<>();
        testInput.put("key1", "value1");
        testInput.put("key2", 123);
        testDto = new TestDto("test");
    }

    @Test
    void shouldNotFilterCreateInput() {
        Map<String, Object> result = adapter.filterInput(DalOperationType.CREATE, testInput, mockAuthentication);
        assertSame(testInput, result);
        assertEquals(testInput, result);
    }

    @Test
    void shouldNotFilterUpdateInput() {
        Map<String, Object> result = adapter.filterInput(DalOperationType.UPDATE, testInput, mockAuthentication);
        assertSame(testInput, result);
        assertEquals(testInput, result);
    }

    @Test
    void shouldNotFilterCreateOutput() {
        assertSame(testDto, adapter.filterOutput(DalOperationType.CREATE, testDto, mockAuthentication));
    }

    @Test
    void shouldNotFilterReadOutput() {
        assertSame(testDto, adapter.filterOutput(DalOperationType.READ, testDto, mockAuthentication));
    }

    @Test
    void shouldNotFilterReadOneOutput() {
        assertSame(testDto, adapter.filterOutput(DalOperationType.READ_ONE, testDto, mockAuthentication));
    }

    @Test
    void shouldNotFilterUpdateOutput() {
        assertSame(testDto, adapter.filterOutput(DalOperationType.UPDATE, testDto, mockAuthentication));
    }

    record TestDto(String name) {
    }
}
