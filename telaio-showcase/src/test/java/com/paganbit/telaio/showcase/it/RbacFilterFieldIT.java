package com.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
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
