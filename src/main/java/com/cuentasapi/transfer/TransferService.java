package com.cuentasapi.transfer;

import com.cuentasapi.account.Account;
import com.cuentasapi.account.AccountRepository;
import com.cuentasapi.account.EntryType;
import com.cuentasapi.account.LedgerEntry;
import com.cuentasapi.account.LedgerEntryRepository;
import com.cuentasapi.audit.AuditService;
import com.cuentasapi.common.ApiException;
import com.cuentasapi.common.Money;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferService {

    /** What the client receives: the stored status and JSON body, identical on every replay. */
    public record StoredResponse(int status, String body) {
    }

    public record TransferRequest(long sourceAccountId, long targetAccountId, long amount) {
    }

    public record TransferResponse(long id, long sourceAccountId, long targetAccountId, long amount, String status,
                                   Instant createdAt) {
        static TransferResponse of(Transfer t) {
            return new TransferResponse(t.getId(), t.getSourceAccountId(), t.getTargetAccountId(), t.getAmount(),
                    t.getStatus(), t.getCreatedAt());
        }
    }

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final TransferRepository transfers;
    private final AuditService audit;
    private final IdempotencyRepository idempotency;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public TransferService(AccountRepository accounts, LedgerEntryRepository entries, TransferRepository transfers,
                           AuditService audit, IdempotencyRepository idempotency, ObjectMapper json,
                           PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.entries = entries;
        this.transfers = transfers;
        this.audit = audit;
        this.idempotency = idempotency;
        this.json = json;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Runs the transfer in one transaction, keyed by (user, Idempotency-Key). The key row is inserted first: a
     * concurrent request with the same key waits on the unique index, then fails and replays the stored response.
     * A failed transfer rolls back its key row too, so the same key can be retried once the cause is fixed. Failures
     * are audited afterwards, outside the transaction: auditing inside it would need a second connection per request.
     */
    public StoredResponse transfer(long userId, String key, TransferRequest request) {
        String hash = hash(request);
        try {
            return tx.execute(status -> {
                IdempotencyKey row = idempotency.saveAndFlush(new IdempotencyKey(userId, key, hash));
                String body = toJson(execute(userId, request));
                row.complete(201, body);
                return new StoredResponse(201, body);
            });
        } catch (DataIntegrityViolationException possibleDuplicate) {
            return replay(userId, key, hash, possibleDuplicate);
        } catch (ApiException e) {
            audit.recordIndependent(userId, "TRANSFER", target(request), "FAILED: " + e.getMessage());
            throw e;
        }
    }

    private StoredResponse replay(long userId, String key, String hash, DataIntegrityViolationException original) {
        IdempotencyKey stored = idempotency.findByUserIdAndRequestKey(userId, key).orElseThrow(() -> original);
        if (!stored.getRequestHash().equals(hash)) {
            throw ApiException.unprocessable("Idempotency-Key was already used with a different request");
        }
        if (stored.getResponseBody() == null) {
            throw original;
        }
        return new StoredResponse(stored.getResponseStatus(), stored.getResponseBody());
    }

    private String toJson(TransferResponse response) {
        try {
            return json.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hash(TransferRequest request) {
        String canonical = request.sourceAccountId() + ":" + request.targetAccountId() + ":" + request.amount();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private TransferResponse execute(long userId, TransferRequest request) {
        long sourceId = request.sourceAccountId();
        long targetId = request.targetAccountId();
        if (sourceId == targetId) {
            throw ApiException.badRequest("Source and target accounts must be different");
        }
        Money.requireValid(request.amount());

        // Both rows locked in ascending id order, whatever the direction of the transfer.
        List<Account> locked = accounts.lockAllById(List.of(sourceId, targetId));
        Account source = locked.stream().filter(a -> a.getId() == sourceId).findFirst().orElse(null);
        Account target = locked.stream().filter(a -> a.getId() == targetId).findFirst().orElse(null);
        if (source == null || source.getUserId() != userId || target == null) {
            throw ApiException.notFound("Account not found");
        }
        if (source.isFrozen() || target.isFrozen()) {
            throw ApiException.conflict("Account is frozen");
        }
        if (source.getBalance() < request.amount()) {
            throw ApiException.unprocessable("Insufficient balance");
        }

        Transfer transfer = transfers.save(new Transfer(sourceId, targetId, request.amount()));
        source.debit(request.amount());
        target.credit(request.amount());
        entries.save(new LedgerEntry(sourceId, transfer.getId(), EntryType.DEBIT, request.amount(), source.getBalance()));
        entries.save(new LedgerEntry(targetId, transfer.getId(), EntryType.CREDIT, request.amount(), target.getBalance()));
        audit.record(userId, "TRANSFER", target(request), "OK");
        return TransferResponse.of(transfer);
    }

    private static String target(TransferRequest request) {
        return "account:" + request.sourceAccountId() + "->" + request.targetAccountId();
    }

    /** The owner of the source account or an operator; anyone else gets a 404. */
    public TransferResponse get(long id, long userId, boolean operator) {
        Transfer transfer = transfers.findById(id).orElseThrow(() -> ApiException.notFound("Transfer not found"));
        Account source = accounts.findById(transfer.getSourceAccountId()).orElseThrow();
        if (!operator && source.getUserId() != userId) {
            throw ApiException.notFound("Transfer not found");
        }
        return TransferResponse.of(transfer);
    }
}
