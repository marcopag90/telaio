package com.paganbit.telaio.showcase.it;

import com.paganbit.telaio.audit.event.DalAuditEvent;
import com.paganbit.telaio.audit.event.DalAuditOutcome;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.showcase.it.AuditCaptureTestConfig.CapturingDalAuditEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-cutting — field-level RBAC also governs the {@code sort=} keys. A principal may not sort on a
 * property the RBAC adapter hides from them: the ordering would otherwise reveal the relative values
 * ({@code sort=cost_price,desc&size=1} names the product with the highest margin). Covered for both
 * built-in adapters — property maps ({@code products}) and {@code @JsonView} ({@code employees}) — under
 * the wire name and the Java name alike. The rejection is the same generic 400 as an unknown sort
 * property, so the sort cannot be used to probe which hidden properties exist either; on the audited
 * {@code products} DAL the attempt is recorded as a DENIED audit event.
 */
class RbacSortFieldIT extends AbstractShowcaseIT {

    @Autowired
    private CapturingDalAuditEventStore auditStore;

    @Test
    void propertyMapAdapter_rejectsASortOnAHiddenField() {
        assertInvalidSort(list(USER, "products", "sort=cost_price,desc&size=1"));
        assertInvalidSort(list(USER, "products", "sort=costPrice,desc"));
        assertInvalidSort(list(ADMIN, "products", "sort=internal_sku,asc"));
    }

    @Test
    void propertyMapAdapter_stillSortsOnReadableFields() {
        ResponseEntity<String> developer = list(DEVELOPER, "products", "sort=cost_price,desc&size=100");
        ResponseEntity<String> user = list(USER, "products", "sort=price,asc&size=100");

        assertThat(developer.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<BigDecimal> costPrices = decimals(developer, "cost_price");
        assertThat(costPrices).isNotEmpty().isSortedAccordingTo(Comparator.reverseOrder());

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decimals(user, "price")).isNotEmpty().isSorted();
    }

    @Test
    void jsonViewAdapter_rejectsASortOnAFieldOutsideTheView() {
        assertInvalidSort(list(USER, "employees", "sort=salary,desc"));
        assertInvalidSort(list(ADMIN, "employees", "sort=salary,desc"));
        assertInvalidSort(list(USER, "employees", "sort=employeeEmail,asc"));
        assertInvalidSort(list(USER, "employees", "sort=email,asc"));
    }

    @Test
    void jsonViewAdapter_stillSortsOnFieldsInTheView() {
        ResponseEntity<String> developer = list(DEVELOPER, "employees", "sort=salary,desc&size=100");
        ResponseEntity<String> user = list(USER, "employees", "sort=employeeName,asc&size=100");

        assertThat(developer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decimals(developer, "salary")).isNotEmpty().isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void sortOnAHiddenField_isAuditedAsDenied() {
        auditStore.clear();

        assertInvalidSort(list(USER, "products", "sort=cost_price,desc&size=1"));

        assertThat(auditStore.events())
            .as("probing a hidden property through sort= is an authorization signal, not a validation slip")
            .anySatisfy(this::assertDeniedProductRead);
    }

    private void assertDeniedProductRead(DalAuditEvent event) {
        assertThat(event.dalName()).isEqualTo("products");
        assertThat(event.operation()).isEqualTo(DalOperationType.READ);
        assertThat(event.outcome()).isEqualTo(DalAuditOutcome.DENIED);
        assertThat(event.principal()).isEqualTo(USER);
    }

    private List<BigDecimal> decimals(ResponseEntity<String> response, String field) {
        List<BigDecimal> values = new ArrayList<>();
        for (JsonNode row : tree(response).path("content")) {
            values.add(row.path(field).decimalValue());
        }
        return values;
    }
}
