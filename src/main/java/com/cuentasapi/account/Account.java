package com.cuentasapi.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String number;

    /** Centavos. The database also enforces balance >= 0. */
    @Column(nullable = false)
    private long balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Version
    private long version;

    protected Account() {
    }

    public Account(Long userId, String number) {
        this.userId = userId;
        this.number = number;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNumber() {
        return number;
    }

    public long getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isFrozen() {
        return status == AccountStatus.FROZEN;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public void credit(long amount) {
        balance += amount;
    }

    public void debit(long amount) {
        balance -= amount;
    }
}
