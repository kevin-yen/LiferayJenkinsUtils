package com.liferay.jenkins.tools;

public class UnableToReadConfigException extends Exception {

    public UnableToReadConfigException() {
    }

    public UnableToReadConfigException(String message) {
        super(message);
    }

    public UnableToReadConfigException(Throwable cause) {
        super(cause);
    }

    public UnableToReadConfigException(String message, Throwable cause) {
        super(message, cause);
    }

}
