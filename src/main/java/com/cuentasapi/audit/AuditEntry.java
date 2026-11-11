package com.cuentasapi.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false)
    private String action;

    private String target;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEntry() {
    }

    public AuditEntry(Long actorId, String action, String target, String outcome) {
        this.actorId = actorId;
        this.action = action;
        this.target = target;
        this.outcome = outcome;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
