package com.railway.common.exception;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String resourceType, Object identifier) {
        super("DUPLICATE_RESOURCE", resourceType + " already exists with identifier: " + identifier);
    }
}
