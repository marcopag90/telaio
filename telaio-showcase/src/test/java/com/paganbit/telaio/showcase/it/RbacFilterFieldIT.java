package com.paganbit.telaio.showcase.it;

import com.paganbit.telaio.audit.event.DalAuditOutcome;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.showcase.it.AuditCaptureTestConfig.CapturingDalAuditEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-cutting — field-level RBAC also governs the {@code q=} filter. A principal may not filter on a
 * property the RBAC adapter hides from them: the rows that come back would otherwise reveal its value
 * (bisection through {@code cost_price > 100}). Covered for both built-in adapters — property maps
 * ({@code products}: {@code cost_price}/{@code internal_sku} readable by DEVELOPER only) and
 * {@code @JsonView} ({@code employees}: {@code salary} in the Developer view, {@code employeeEmail} from the
 * Admin view up) — under the wire name and the Java name alike. The rejection is the same generic 400 as an
 * unknown field, so the filter cannot be used to probe which hidden properties exist either.
 */
class RbacFilterFieldIT extends AbstractShowcaseIT {

    @Autowired
    private CapturingDalAuditEventStore auditStore;

    @Test
    void propertyMapAdapter_rejectsAFilterOnAHiddenField() {
        assertInvalidFilter(list(USER, "products", "q=cost_price>100"));
        assertInvalidFilter(list(USER, "products", "q=costPrice>100"));
        assertInvalidFilter(list(ADMIN, "products", "q=internal_sku:'x'"));
    }

    @Test
    void propertyMapAdapter_stillFiltersOnReadableFields() {
        ResponseEntity<String> developer = list(DEVELOPER, "products", "q=cost_price>0");
        ResponseEntity<String> user = list(USER, "products", "q=price>0");

        assertThat(developer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(developer).path("page").path("totalElements").asLong()).isPositive();
        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(user).path("page").path("totalElements").asLong()).isPositive();
    }

    @Test
    void jsonViewAdapter_rejectsAFilterOnAFieldOutsideTheView() {
        assertInvalidFilter(list(USER, "employees", "q=salary>1000"));
        assertInvalidFilter(list(ADMIN, "employees", "q=salary>1000"));
        assertInvalidFilter(list(USER, "employees", "q=employeeEmail~'*a*'"));
        assertInvalidFilter(list(USER, "employees", "q=email~'*a*'"));
    }

    @Test
    void filterOnANonexistentField_isAuditedAsValidation_notDenied() {
        // The field resolves on no property at all: a typo (or a blind probe), not an authorization
        // signal — the audit outcome must stay VALIDATION even under RBAC.
        auditStore.clear();

        assertInvalidFilter(list(ADMIN, "products", "q=nonexistent:1"));

        assertThat(auditStore.events())
            .anySatisfy(event -> {
                assertThat(event.dalName()).isEqualTo("products");
                assertThat(event.operation()).isEqualTo(DalOperationType.READ);
                assertThat(event.outcome()).isEqualTo(DalAuditOutcome.VALIDATION);
                assertThat(event.principal()).isEqualTo(ADMIN);
                assertThat(event.errorType())
                    .isEqualTo("com.paganbit.telaio.core.exception.DalInvalidFilterException");
            })
            .noneSatisfy(event -> assertThat(event.outcome()).isEqualTo(DalAuditOutcome.DENIED));
    }

    @Test
    void filterOnAHiddenField_isAuditedAsDenied() {
        // cost_price exists and is hidden from USER: probing it stays a denied attempt — identical 400
        // on the wire, distinguishable server-side from the nonexistent field above.
        auditStore.clear();

        assertInvalidFilter(list(USER, "products", "q=cost_price>100"));

        assertThat(auditStore.events()).anySatisfy(event -> {
            assertThat(event.dalName()).isEqualTo("products");
            assertThat(event.outcome()).isEqualTo(DalAuditOutcome.DENIED);
            assertThat(event.principal()).isEqualTo(USER);
            assertThat(event.errorType())
                .isEqualTo("com.paganbit.telaio.core.exception.DalFilterFieldNotReadableException");
        });
    }

    @Test
    void jsonViewAdapter_stillFiltersOnFieldsInTheView() {
        ResponseEntity<String> developer = list(DEVELOPER, "employees", "q=salary>0");
        ResponseEntity<String> admin = list(ADMIN, "employees", "q=employeeEmail~'*@*'");
        ResponseEntity<String> user = list(USER, "employees", "q=employeeName~'*a*'");

        assertThat(developer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree(user).path("page").path("totalElements").asLong()).isPositive();
    }
}
