package com.cuentasapi.account;

import com.cuentasapi.audit.AuditService;
import com.cuentasapi.common.ApiException;
import com.cuentasapi.common.Money;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final AuditService audit;

    public AccountService(AccountRepository accounts, LedgerEntryRepository entries, AuditService audit) {
        this.accounts = accounts;
        this.entries = entries;
        this.audit = audit;
    }

    @Transactional
    public Account open(long userId) {
        Account account = accounts.save(new Account(userId, String.valueOf(accounts.nextNumber())));
        audit.record(userId, "ACCOUNT_OPENED", "account:" + account.getId(), "OK");
        return account;
    }

    /** Someone else's account is a 404, never a 403, so its existence is not revealed. */
    @Transactional(readOnly = true)
    public Account get(long id, long userId, boolean operator) {
        return accounts.findById(id)
                .filter(a -> operator || a.getUserId() == userId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
    }

    @Transactional(readOnly = true)
    public Page<Account> list(long userId, boolean operator, Pageable pageable) {
        return operator ? accounts.findAll(pageable) : accounts.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> entries(long accountId, long userId, boolean operator, Pageable pageable) {
        get(accountId, userId, operator);
        return entries.findByAccountId(accountId, pageable);
    }

    @Transactional
    public Account deposit(long accountId, long amount, long operatorId) {
        Money.requireValid(amount);
        Account account = lock(accountId);
        if (account.isFrozen()) {
            throw ApiException.conflict("Account is frozen");
        }
        account.credit(amount);
        entries.save(new LedgerEntry(accountId, null, EntryType.CREDIT, amount, account.getBalance()));
        audit.record(operatorId, "DEPOSIT", "account:" + accountId, "OK");
        return account;
    }

    /** Idempotent: freezing a frozen account (or unfreezing an active one) changes nothing but is still audited. */
    @Transactional
    public Account setFrozen(long accountId, boolean frozen, long operatorId) {
        Account account = lock(accountId);
        account.setStatus(frozen ? AccountStatus.FROZEN : AccountStatus.ACTIVE);
        audit.record(operatorId, frozen ? "FREEZE" : "UNFREEZE", "account:" + accountId, "OK");
        return account;
    }

    private Account lock(long accountId) {
        return accounts.lockAllById(List.of(accountId)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Account not found"));
    }
}
