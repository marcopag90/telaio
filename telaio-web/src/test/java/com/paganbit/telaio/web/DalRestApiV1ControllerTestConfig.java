package com.paganbit.telaio.web;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.web.annotation.DalIdArgumentResolver;
import com.paganbit.telaio.web.autoconfigure.TelaioWebAutoConfiguration;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.autoconfigure.web.DataWebAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@TestConfiguration
@Import(value = {
    DalIdArgumentResolver.class,
    TelaioWebAutoConfiguration.class
})
@ImportAutoConfiguration(DataWebAutoConfiguration.class)
public class DalRestApiV1ControllerTestConfig {

    public static class MockDalService implements Dal<Object, Long> {

        private static boolean mockUpdateWithNoContent = false;

        public static void setMockUpdateWithNoContent(boolean mockUpdateWithNoContent) {
            MockDalService.mockUpdateWithNoContent = mockUpdateWithNoContent;
        }

        @Override
        public Object create(Map<String, Object> properties) {
            return Map.of("id", 1L, "name", properties.get("name"));
        }

        @Override
        public Page<Object> read(@Nullable FilterNode filter, Pageable pageable) {
            // Honor the resolved Pageable so the page metadata mirrors production (paged), not an
            // unpaged page whose size would just echo the content length.
            List<Object> content = List.of(
                Map.of("id", 1L, "name", "Company One"),
                Map.of("id", 2L, "name", "Company Two")
            );
            return new PageImpl<>(content, pageable, content.size());
        }

        @Override
        public Optional<Object> readOne(Long id) {
            return Optional.of(Map.of("id", id, "name", "Company Single"));
        }

        @Override
        public Optional<Object> update(Long id, Map<String, Object> properties) {
            if (mockUpdateWithNoContent) {
                return Optional.empty();
            }
            return Optional.of(Map.of("id", id, "name", properties.get("name")));
        }

        @Override
        public void delete(Long id) {
            //noop
        }

        @Override
        public Class<Object> getEntityClass() {
            return Object.class;
        }

        @Override
        public Class<Long> getIdClass() {
            return Long.class;
        }
    }
}
