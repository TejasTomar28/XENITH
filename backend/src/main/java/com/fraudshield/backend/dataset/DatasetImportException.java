package com.fraudshield.backend.dataset;

public class DatasetImportException extends RuntimeException {
    public DatasetImportException(String message) { super(message); }
    public DatasetImportException(String message, Throwable cause) { super(message, cause); }
}
