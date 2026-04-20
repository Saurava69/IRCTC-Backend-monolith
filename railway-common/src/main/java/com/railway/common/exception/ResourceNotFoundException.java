package com.railway.common.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super("RESOURCE_NOT_FOUND", resourceType + " not found with identifier: " + identifier);
    }
}
