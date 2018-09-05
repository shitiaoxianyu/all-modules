package com.earnest.video.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class UnknownException extends RuntimeException {

    public UnknownException() {
        super();
    }

    public UnknownException(String message) {
        super("server error:" + message);
    }


    public UnknownException(Throwable cause) {
        this(cause.getMessage());
    }

    protected UnknownException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
