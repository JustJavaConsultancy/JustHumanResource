package com.justjava.humanresource.common.exception;


public class ResourceNotFoundException extends CustomException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found with id: " + id);
    }
}
