package site.devtown.spadeworker.global.exception;

import javax.persistence.EntityNotFoundException;

public class ResourceNotFoundException
        extends EntityNotFoundException {
    private final ExceptionCode code;

    public ResourceNotFoundException(
            ExceptionCode exceptionCode
    ) {
        super(exceptionCode.getMessage());
        this.code = exceptionCode;
    }

    public ExceptionCode getCode() {
        return code;
    }
}
