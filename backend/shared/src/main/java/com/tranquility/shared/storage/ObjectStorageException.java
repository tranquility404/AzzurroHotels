package com.tranquility.shared.storage;

public class ObjectStorageException extends RuntimeException {
    public Exception srcExc;
    public ObjectStorageException(String message, Exception e) {
        super(message);
        this.srcExc = e;
    }
}