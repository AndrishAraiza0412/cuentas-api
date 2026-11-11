package com.cuentasapi.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "target_account_id", nullable = false)
    private Long targetAccountId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Transfer() {
    }

    public Transfer(Long sourceAccountId, Long targetAccountId, long amount) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.status = "COMPLETED";
    }

    public Long getId() {
        return id;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public Long getTargetAccountId() {
        return targetAccountId;
    }

    public long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
