package com.wego.toursoperator.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * Multipart parsing runs before a handler is selected, so a controller/package
 * selector cannot catch its size failure. Handle only that transport exception
 * in this isolated product, without opening /error or weakening authentication.
 */
@RestControllerAdvice
class MediaTransportExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun oversizedMultipart(): ResponseEntity<UploadErrorResponse> =
        ResponseEntity
            .status(413)
            .header("Cache-Control", "no-store, private")
            .body(UploadErrorResponse("file_too_large"))
}
