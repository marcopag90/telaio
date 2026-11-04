package com.paganbit.telaio.web.channel;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.web.annotation.DalWeb;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DalRestChannelDefinitionTest {

    private final DalRestChannelDefinition definition = new DalRestChannelDefinition();

    @Test
    void channel_isRest() {
        assertThat(definition.channel()).isEqualTo(DalChannels.REST);
    }

    @Test
    void withoutDalWeb_everyOperationIsExposed() {
        assertThat(definition.exposedOperations(PlainDal.class))
            .isEqualTo(EnumSet.allOf(DalOperationType.class));
    }

    @Test
    void withDalWeb_onlyTheListedOperationsAreExposed() {
        assertThat(definition.exposedOperations(ReadOnlyDal.class))
            .containsExactlyInAnyOrder(DalOperationType.READ, DalOperationType.READ_ONE);
    }

    @Test
    void withEmptyDalWeb_nothingIsExposed() {
        assertThat(definition.exposedOperations(HiddenDal.class)).isEmpty();
    }

    @Test
    void bareDalWeb_exposesEveryOperation() {
        assertThat(definition.exposedOperations(BareDal.class))
            .isEqualTo(EnumSet.allOf(DalOperationType.class));
    }

    @Test
    void dalWebOnASuperclass_appliesToTheSubclass() {
        assertThat(definition.exposedOperations(InheritingDal.class))
            .containsExactlyInAnyOrder(DalOperationType.READ, DalOperationType.READ_ONE);
    }

    @Test
    void theAnswerIsImmutable() {
        Set<DalOperationType> exposed = definition.exposedOperations(ReadOnlyDal.class);

        assertThatThrownBy(() -> exposed.add(DalOperationType.DELETE))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    abstract static class PlainDal implements Dal<Object, Long> {
    }

    @DalWeb
    abstract static class BareDal implements Dal<Object, Long> {
    }

    @DalWeb(operations = {DalOperationType.READ, DalOperationType.READ_ONE})
    abstract static class ReadOnlyDal implements Dal<Object, Long> {
    }

    abstract static class InheritingDal extends ReadOnlyDal {
    }

    @DalWeb(operations = {})
    abstract static class HiddenDal implements Dal<Object, Long> {
    }
}
