package com.concordeu.catalog.exception;

import com.concordeu.catalog.exception.ControllerExceptionHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class OptimisticLockHandlingTest {

    private final ControllerExceptionHandler handler = new ControllerExceptionHandler();

    @Test
    void should_return409ProblemDetail_when_optimisticLockFailure() {
        ResponseEntity<ProblemDetail> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Product", "id-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "CONFLICT");
    }
}
