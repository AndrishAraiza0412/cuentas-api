package com.cuentasapi.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.cuentasapi.auth.CurrentUser;
import com.cuentasapi.common.PageResponse;
import com.cuentasapi.common.PageSizes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/accounts")
public class AccountController {

    public record AccountResponse(long id, String number, long balance, String status) {
        static AccountResponse of(Account a) {
            return new AccountResponse(a.getId(), a.getNumber(), a.getBalance(), a.getStatus().name());
        }
    }

    public record EntryResponse(long id, String type, long amount, long balanceAfter, Long transferId, Instant createdAt) {
        static EntryResponse of(LedgerEntry e) {
            return new EntryResponse(e.getId(), e.getType().name(), e.getAmount(), e.getBalanceAfter(),
                    e.getTransferId(), e.getCreatedAt());
        }
    }

    public record DepositRequest(@NotNull Long amount) {
    }

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open an account for the caller (CLIENTE)")
    public AccountResponse open() {
        return AccountResponse.of(accounts.open(CurrentUser.id()));
    }

    @GetMapping
    @Operation(summary = "List accounts (own for CLIENTE, all for OPERADOR)")
    public PageResponse<AccountResponse> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "" + PageSizes.DEFAULT) int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), PageSizes.cap(size), Sort.by("id"));
        return PageResponse.of(accounts.list(CurrentUser.id(), CurrentUser.isOperator(), request), AccountResponse::of);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one account")
    public AccountResponse get(@PathVariable long id) {
        return AccountResponse.of(accounts.get(id, CurrentUser.id(), CurrentUser.isOperator()));
    }

    @GetMapping("/{id}/entries")
    @Operation(summary = "Ledger history, newest first (page size capped at 100)")
    public PageResponse<EntryResponse> entries(@PathVariable long id,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "" + PageSizes.DEFAULT) int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), PageSizes.cap(size), Sort.by(Sort.Order.desc("id")));
        return PageResponse.of(accounts.entries(id, CurrentUser.id(), CurrentUser.isOperator(), request), EntryResponse::of);
    }

    @PostMapping("/{id}/deposits")
    @Operation(summary = "Credit an account (OPERADOR)")
    public AccountResponse deposit(@PathVariable long id, @Valid @RequestBody DepositRequest request) {
        return AccountResponse.of(accounts.deposit(id, request.amount(), CurrentUser.id()));
    }

    @PostMapping("/{id}/freeze")
    @Operation(summary = "Freeze an account (OPERADOR)")
    public AccountResponse freeze(@PathVariable long id) {
        return AccountResponse.of(accounts.setFrozen(id, true, CurrentUser.id()));
    }

    @PostMapping("/{id}/unfreeze")
    @Operation(summary = "Unfreeze an account (OPERADOR)")
    public AccountResponse unfreeze(@PathVariable long id) {
        return AccountResponse.of(accounts.setFrozen(id, false, CurrentUser.id()));
    }
}
