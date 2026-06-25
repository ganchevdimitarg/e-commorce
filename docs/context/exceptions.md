# Exception Hierarchy

All domain exceptions extend `BusinessException`:

```java
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String     code;
    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code   = code;
    }
    public HttpStatus getStatus() { return status; }
    public String     getCode()   { return code; }
}

public class NotFoundException extends BusinessException {
    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found: " + id);
    }
}

public class ConflictException extends BusinessException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}

public class ValidationException extends BusinessException {
    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
```

`@ControllerAdvice` maps every `BusinessException` → `application/problem+json` (RFC 9457).
Never catch and rethrow as plain `RuntimeException`.
