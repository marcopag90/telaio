package com.paganbit.telaio.core.beans.registration;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.exception.DalDefinitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DalFactoryPostProcessorTest {

    @Mock
    private ConfigurableListableBeanFactory beanFactory;

    private DalFactoryPostProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DalFactoryPostProcessor();
    }

    @Test
    void postProcessBeanFactory_withoutDalBeans_shouldCompleteSilently() {
        when(beanFactory.getBeanNamesForType(Dal.class)).thenReturn(new String[0]);

        assertDoesNotThrow(() -> processor.postProcessBeanFactory(beanFactory));

        verify(beanFactory, never()).findAnnotationOnBean(anyString(), eq(DalService.class));
    }

    @Test
    void postProcessBeanFactory_withAnnotatedDalBean_shouldCompleteSilently() {
        when(beanFactory.getBeanNamesForType(Dal.class)).thenReturn(new String[]{"annotatedDal"});
        when(beanFactory.findAnnotationOnBean("annotatedDal", DalService.class))
            .thenReturn(AnnotatedStub.class.getAnnotation(DalService.class));

        assertDoesNotThrow(() -> processor.postProcessBeanFactory(beanFactory));
    }

    @Test
    void postProcessBeanFactory_withUnannotatedDalBean_shouldThrowDalDefinitionException() {
        when(beanFactory.getBeanNamesForType(Dal.class)).thenReturn(new String[]{"rogueDal"});
        when(beanFactory.findAnnotationOnBean("rogueDal", DalService.class)).thenReturn(null);

        DalDefinitionException exception = assertThrows(DalDefinitionException.class,
            () -> processor.postProcessBeanFactory(beanFactory));

        assertTrue(exception.getMessage().contains(DalService.class.getName()));
        assertTrue(exception.getMessage().contains("[rogueDal]"));
    }

    @DalService(name = "annotatedStub")
    private static class AnnotatedStub {
    }
}
