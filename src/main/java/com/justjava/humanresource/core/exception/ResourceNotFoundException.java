package com.justjava.humanresource.core.exception;


public class ResourceNotFoundException extends CustomException {

    public ResourceNotFoundException(String resource) {
        super(resource + " not found");
    }
}
