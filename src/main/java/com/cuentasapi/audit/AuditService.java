package com.cuentasapi.audit;

import com.cuentasapi.common.PageSizes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditRepository entries;

    public AuditService(AuditRepository entries) {
        this.entries = entries;
    }

    /** Written in the caller's transaction: it commits or rolls back with the action it records. */
    @Transactional
    public void record(Long actorId, String action, String target, String outcome) {
        entries.save(new AuditEntry(actorId, action, target, outcome));
    }

    /** Written in its own transaction, for entries that must survive the caller's rollback (failed attempts). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(Long actorId, String action, String target, String outcome) {
        entries.save(new AuditEntry(actorId, action, target, outcome));
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> search(Long actor, String action, int page, int size) {
        Specification<AuditEntry> filter = Specification.where(null);
        if (actor != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("actorId"), actor));
        }
        if (action != null && !action.isBlank()) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        return entries.findAll(filter,
                PageRequest.of(Math.max(page, 0), PageSizes.cap(size), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }
}
