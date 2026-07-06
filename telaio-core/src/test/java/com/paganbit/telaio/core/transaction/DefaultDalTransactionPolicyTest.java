package com.paganbit.telaio.core.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDalTransactionPolicyTest {

    private DefaultDalTransactionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DefaultDalTransactionPolicy();
    }

    @Nested
    class ReadPolicies {

        @Test
        void forRead_shouldBeReadOnly() {
            DefaultTransactionDefinition def = policy.forRead();
            assertTrue(def.isReadOnly(), "read transaction must be readOnly = true");
        }

        @Test
        void forReadOne_shouldBeReadOnly() {
            DefaultTransactionDefinition def = policy.forReadOne();
            assertTrue(def.isReadOnly(), "readOne transaction must be readOnly = true");
        }
    }

    @Nested
    class WritePolicies {

        @Test
        void forCreate_shouldNotBeReadOnly() {
            DefaultTransactionDefinition def = policy.forCreate();
            assertFalse(def.isReadOnly(), "create transaction must be readOnly = false");
        }

        @Test
        void forUpdate_shouldNotBeReadOnly() {
            DefaultTransactionDefinition def = policy.forUpdate();
            assertFalse(def.isReadOnly(), "update transaction must be readOnly = false");
        }

        @Test
        void forDelete_shouldNotBeReadOnly() {
            DefaultTransactionDefinition def = policy.forDelete();
            assertFalse(def.isReadOnly(), "delete transaction must be readOnly = false");
        }
    }
}
