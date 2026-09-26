package com.crosspath.idservice.api;

import com.crosspath.idservice.api.dto.RegistrationRequest;
import com.crosspath.idservice.api.dto.RegistrationResponse;
import com.crosspath.idservice.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/v1/registrations")
public class RegistrationController {

    private static final DateTimeFormatter UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegistrationResponse> register(
            @RequestBody RegistrationRequest body,
            @RequestHeader("Authorization") String authorization,
            HttpServletRequest request) {

        // secret 検証
        String secret = extractBearerSecret(authorization);
        CredentialSecret credentialSecret = new CredentialSecret(secret);
        byte[] credentialHash = credentialSecret.getHash();

        // requestId 検証
        UUID requestId = RequestIdValidator.validate(body.getRequestId());

        RegistrationResult result = registrationService.register(requestId, credentialHash);

        RegistrationResponse response = buildResponse(result);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        if (result.isNewRegistration()) {
            return ResponseEntity.status(HttpStatus.CREATED).headers(headers).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.OK).headers(headers).body(response);
        }
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegistrationResponse> getMe(
            @RequestHeader("Authorization") String authorization) {

        String secret = extractBearerSecret(authorization);
        CredentialSecret credentialSecret = new CredentialSecret(secret);
        byte[] credentialHash = credentialSecret.getHash();

        RegistrationResult result = registrationService.findByCredentialHash(credentialHash);
        RegistrationResponse response = buildResponse(result);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResponseEntity.ok().headers(headers).body(response);
    }

    private RegistrationResponse buildResponse(RegistrationResult result) {
        return new RegistrationResponse(
                result.getRequestId().toString(),
                result.getUserId(),
                UTC_FORMATTER.format(result.getCreatedAt())
        );
    }

    static String extractBearerSecret(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_CREDENTIAL);
        }
        String[] parts = authorization.split(" ", 2);
        if (parts.length != 2 || !"Bearer".equals(parts[0]) || parts[1].isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_CREDENTIAL);
        }
        return parts[1];
    }

}