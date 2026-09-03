package com.paganbit.telaio.showcase.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-cutting — generic REST API error handling that is independent of any single DAL: authentication
 * is required, an unknown DAL name is a 404, a malformed filter is rejected rather than silently
 * ignored (which would otherwise leak every row), a filter the entity cannot honor (unknown field or
 * function) is a 400 and an unconvertible literal a 500 — on the JPA and the Mongo backend alike. The
 * {@code sort=} parameter follows the same contract: an unknown or non-persistent property is a 400 on
 * every backend (previously a 500 on JPA and a silently unsorted 200 on Mongo), and wire names work.
 */
class DalApiErrorsIT extends AbstractShowcaseIT {

    @Test
    void unauthenticatedAccessIsRejectedWith401() {
        assertThat(list(null, "products", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownDalNameReturns404() {
        assertThat(list(USER, "no-such-dal", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void malformedFilterIsRejectedWithBadRequest() {
        // Passed raw (TestRestTemplate encodes once); unbalanced parens are a Turkraft syntax error.
        ResponseEntity<String> response = list(USER, "products", "q=(((");

        assertThat(response.getStatusCode())
            .as("a malformed filter must never be treated as 'no filter' and return rows")
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tree(response).path("detail").asString()).isEqualTo("Malformed filter expression");
    }

    @Test
    void unknownFilterFieldIsRejectedWithBadRequestOnEveryBackend() {
        // Same well-formed filter, same answer on a JPA DAL and on a Mongo DAL: a field the entity does
        // not expose is a client fault (previously a 500 on JPA and a silently empty page on Mongo).
        // On the RBAC-guarded products DAL the unknown field falls through the security check and is
        // rejected by the strict rewrite itself.
        assertInvalidFilter(list(USER, "products", "q=nope:1"));
        assertInvalidFilter(list(USER, "notifications", "q=nope:'x'"));
    }

    @Test
    void filterOnSerializedButNotPersistedPropertyIsRejectedWithBadRequest() {
        // Product.profit is @Transient (computed by the DAL hooks) yet serialized in every response: the
        // name resolves on the wire, but the persistence unit cannot filter on it — a 400, not a 500.
        // DEVELOPER may read profit, so this genuinely reaches the backend validator (for the other
        // roles the RBAC check rejects it first, with the same 400).
        assertInvalidFilter(list(DEVELOPER, "products", "q=profit>10"));
    }

    @Test
    void unconvertibleFilterLiteralIsAServerFaultOnEveryBackend() {
        // By decision a literal that does not convert to the field's type is NOT a client fault: it fails
        // inside the persistence layer and surfaces as a 500 — the same way on a JPA and on a Mongo DAL.
        assertThat(list(USER, "products", "q=price:'abc'").getStatusCode())
            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(list(USER, "notifications", "q=createdAt:'yesterday'").getStatusCode())
            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void unknownFilterFunctionIsRejectedWithBadRequestOnEveryBackend() {
        // Rejected by the parser (no definition for the function) before any backend is reached.
        assertInvalidFilter(list(USER, "products", "q=nosuchfn(price)>1"));
        assertInvalidFilter(list(USER, "notifications", "q=nosuchfn(channel):'x'"));
    }

    @Test
    void unknownSortPropertyIsRejectedWithBadRequestOnEveryBackend() {
        // Same sort, same answer on a JPA DAL and on a Mongo DAL: a property the entity does not expose
        // is a client fault (previously a PropertyReferenceException 500 on JPA and a silent 200 on Mongo).
        // On the RBAC-guarded products DAL the unknown key falls through the security check and is
        // rejected by the sort rewriter itself.
        assertInvalidSort(list(USER, "products", "sort=nope,asc"));
        assertInvalidSort(list(USER, "notifications", "sort=nope,asc"));
    }

    @Test
    void sortOnSerializedButNotPersistedPropertyIsRejectedWithBadRequest() {
        // Product.profit is @Transient (computed by the DAL hooks) yet serialized in every response: the
        // name resolves on the wire, but the persistence unit cannot order by it — a 400, not a 500.
        // DEVELOPER may read profit, so this genuinely reaches the backend validator (for the other
        // roles the RBAC check rejects it first, with the same 400).
        assertInvalidSort(list(DEVELOPER, "products", "sort=profit,desc"));
    }

    @Test
    void renamedFieldStillSortsThroughTheRewriter() {
        // The sort rewrite must honor the wire name and the Java name alike: both spellings produce the
        // same ordering.
        ResponseEntity<String> byWireName = list(DEVELOPER, "products", "sort=cost_price,asc&size=1");
        ResponseEntity<String> byJavaName = list(DEVELOPER, "products", "sort=costPrice,asc&size=1");

        assertThat(byWireName.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byJavaName.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Compare the sorted value itself: with no tie-breaker, two rows sharing the minimum cost
        // could legitimately swap places between the two queries.
        var cheapestByWireName = tree(byWireName).path("content").path(0).path("cost_price").decimalValue();
        assertThat(cheapestByWireName).isNotNull();
        assertThat(tree(byJavaName).path("content").path(0).path("cost_price").decimalValue())
            .isEqualByComparingTo(cheapestByWireName);
    }

    @Test
    void renamedFieldStillFiltersThroughTheStrictResolver() {
        // The strict field check must not break the wire-name (or Java-name) resolution it sits on top of:
        // both spellings select the same, non-empty set of rows.
        ResponseEntity<String> byWireName = list(DEVELOPER, "products", "q=cost_price>0");
        ResponseEntity<String> byJavaName = list(DEVELOPER, "products", "q=costPrice>0");

        assertThat(byWireName.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byJavaName.getStatusCode()).isEqualTo(HttpStatus.OK);
        long matched = tree(byWireName).path("page").path("totalElements").asLong();
        assertThat(matched).isPositive();
        assertThat(tree(byJavaName).path("page").path("totalElements").asLong()).isEqualTo(matched);
    }
}
