package com.cuentasapi.account;

import com.cuentasapi.audit.AuditService;
import com.cuentasapi.common.ApiException;
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
}
