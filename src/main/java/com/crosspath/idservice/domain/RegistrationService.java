package com.crosspath.idservice.domain;

import com.crosspath.idservice.config.GlobalRateLimiter;
import com.crosspath.idservice.persistence.IdAllocatorRepository;
import com.crosspath.idservice.persistence.RegistrationRepository;
import com.crosspath.idservice.persistence.RegistrationRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 採番トランザクション本体。単一 DB トランザクションで処理する。
 * 仕様 §3.1 の手順 1〜10 を厳守。
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final int MAX_USER_ID = 16777215;

    private final JdbcTemplate jdbcTemplate;
    private final IdAllocatorRepository idAllocatorRepository;
    private final RegistrationRepository registrationRepository;
    private final GlobalRateLimiter globalRateLimiter;

    public RegistrationService(JdbcTemplate jdbcTemplate,
                               IdAllocatorRepository idAllocatorRepository,
                               RegistrationRepository registrationRepository,
                               GlobalRateLimiter globalRateLimiter) {
        this.jdbcTemplate = jdbcTemplate;
        this.idAllocatorRepository = idAllocatorRepository;
        this.registrationRepository = registrationRepository;
        this.globalRateLimiter = globalRateLimiter;
    }

    /**
     * 新規登録（POST /v1/registrations）。
     * 形式検証・secret ハッシュ化は呼出し側が行うこと。
     */
    @Transactional
    public RegistrationResult register(UUID requestId, byte[] credentialHash) {
        try {
            // 手順 2: SET LOCAL lock_timeout = '2s'
            jdbcTemplate.execute("SET LOCAL lock_timeout = '2s'");

            // 手順 3: SELECT next_id FROM id_allocator WHERE singleton = 1 FOR UPDATE
            int nextId;
            try {
                nextId = idAllocatorRepository.selectNextIdForUpdate();
            } catch (PessimisticLockingFailureException | QueryTimeoutException |
                     UncategorizedDataAccessException e) {
                // ロックタイムアウト → 503 REGISTRATION_BUSY（Retry-After: 1）
                // UncategorizedDataAccessException も含める（PostgreSQL の lock_timeout 55P03 が
                // Spring のデフォルト写像で UncategorizedSQLException になるため）
                log.warn("Lock timeout acquiring id_allocator row", e);
                throw new ApiException(ApiErrorCode.REGISTRATION_BUSY, null, 1);
            } catch (DataAccessException e) {
                log.error("id_allocator row missing or inaccessible", e);
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "id_allocator corrupted");
            }

            // 手順 4: request_id 重複チェック
            Optional<RegistrationRow> existingByRid = registrationRepository.findByRequestId(requestId);
            if (existingByRid.isPresent()) {
                RegistrationRow row = existingByRid.get();
                boolean hashMatch = CredentialSecret.constantTimeEquals(row.getCredentialHash(), credentialHash);
                if (hashMatch && "ACTIVE".equals(row.getState())) {
                    // 200: 再送（グローバルレート制限を消費しない）
                    return new RegistrationResult(row.getRequestId(), row.getUserId(), row.getCreatedAt(), false);
                } else if (!hashMatch) {
                    throw new ApiException(ApiErrorCode.REGISTRATION_CONFLICT);
                } else if (hashMatch && "RETIRED".equals(row.getState())) {
                    throw new ApiException(ApiErrorCode.REGISTRATION_RETIRED);
                }
            }

            // 手順 5: credential_hash 重複チェック
            Optional<RegistrationRow> existingByHash = registrationRepository.findByCredentialHash(credentialHash);
            if (existingByHash.isPresent()) {
                throw new ApiException(ApiErrorCode.REGISTRATION_CONFLICT);
            }

            // 手順 6: 枯渇チェック
            if (nextId > MAX_USER_ID) {
                throw new ApiException(ApiErrorCode.ID_SPACE_EXHAUSTED);
            }

            // 手順 6.5: グローバルレート制限（新規発行のみ消費）
            if (!globalRateLimiter.tryConsume()) {
                throw new ApiException(ApiErrorCode.RATE_LIMITED, null, 1);
            }

            // 手順 7: INSERT（DB が生成した created_at を受け取る）
            int userId = nextId;
            OffsetDateTime createdAt;
            try {
                createdAt = registrationRepository.insert(userId, requestId, credentialHash);
            } catch (DataAccessException e) {
                // PK 衝突（台帳不整合）
                log.error("PK conflict on user_id={}, id_allocator may be corrupted", userId, e);
                throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "id_allocator corrupted");
            }

            // 手順 8: UPDATE next_id
            idAllocatorRepository.incrementNextId();

            // 手順 9: commit (transactional により自動 commit)。commit 成功確認後の応答となる。

            return new RegistrationResult(requestId, userId, createdAt, true);
        } catch (PessimisticLockingFailureException | QueryTimeoutException |
                 UncategorizedDataAccessException e) {
            // 全手順共通: ロックタイムアウト全般を 503 REGISTRATION_BUSY に写像
            log.warn("Lock timeout during registration transaction", e);
            throw new ApiException(ApiErrorCode.REGISTRATION_BUSY, null, 1);
        }
    }

    /**
     * GET /v1/registrations/me: credential_hash で検索。
     */
    public RegistrationResult findByCredentialHash(byte[] credentialHash) {
        Optional<RegistrationRow> row = registrationRepository.findByCredentialHash(credentialHash);
        if (row.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_CREDENTIAL);
        }
        RegistrationRow r = row.get();
        if ("RETIRED".equals(r.getState())) {
            throw new ApiException(ApiErrorCode.REGISTRATION_RETIRED);
        }
        return new RegistrationResult(r.getRequestId(), r.getUserId(), r.getCreatedAt(), false);
    }

}