package com.cuentasapi.audit;

import com.cuentasapi.common.PageResponse;
import com.cuentasapi.common.PageSizes;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    public record AuditResponse(long id, Long actorId, String action, String target, String outcome, Instant createdAt) {
    }

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<AuditResponse> list(@RequestParam(required = false) Long actor,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "" + PageSizes.DEFAULT) int size) {
        return PageResponse.of(audit.search(actor, action, page, size),
                e -> new AuditResponse(e.getId(), e.getActorId(), e.getAction(), e.getTarget(), e.getOutcome(), e.getCreatedAt()));
    }
}
