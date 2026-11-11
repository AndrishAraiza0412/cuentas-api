package com.cuentasapi.transfer;

import com.cuentasapi.auth.CurrentUser;
import com.cuentasapi.transfer.TransferService.TransferRequest;
import com.cuentasapi.transfer.TransferService.TransferResponse;
import com.cuentasapi.common.ApiException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestHeader("Idempotency-Key") String key,
                                         @RequestBody TransferRequest request) {
        if (key.isBlank() || key.length() > 80 || !key.matches("\\p{Print}+")) {
            throw ApiException.badRequest("Idempotency-Key must be 1 to 80 printable characters");
        }
        TransferService.StoredResponse stored = transfers.transfer(CurrentUser.id(), key, request);
        return ResponseEntity.status(stored.status()).contentType(MediaType.APPLICATION_JSON).body(stored.body());
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable long id) {
        return transfers.get(id, CurrentUser.id(), CurrentUser.isOperator());
    }
}
