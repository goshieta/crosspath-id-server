package com.crosspath.idservice;

import com.crosspath.idservice.domain.ApiException;
import com.crosspath.idservice.domain.RequestIdValidator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdValidatorTest {

    @Test
    void acceptValidV4() {
        UUID result = RequestIdValidator.validate("78ea85f0-09b5-4b1b-b830-2fcb29711601");
        assertEquals("78ea85f0-09b5-4b1b-b830-2fcb29711601", result.toString());
    }

    @Test
    void rejectV1Uuid() {
        // v1 UUID: version nibble = 1
        assertThrows(ApiException.class,
                () -> RequestIdValidator.validate("f47ac10b-58cc-11e8-9e2c-2f1a2b3c4d5e"));
    }

    @Test
    void rejectV7Uuid() {
        // v7 UUID: version nibble = 7
        assertThrows(ApiException.class,
                () -> RequestIdValidator.validate("017f22e2-79b0-7ccd-a878-0e6b7b9c4d5e"));
    }

    @Test
    void rejectNonUuid() {
        assertThrows(ApiException.class,
                () -> RequestIdValidator.validate("not-a-uuid"));
    }

    @Test
    void rejectNull() {
        assertThrows(ApiException.class,
                () -> RequestIdValidator.validate(null));
    }

    @Test
    void rejectEmptyString() {
        assertThrows(ApiException.class,
                () -> RequestIdValidator.validate(""));
    }

}