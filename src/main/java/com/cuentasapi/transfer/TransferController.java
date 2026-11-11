package com.cuentasapi.transfer;

import com.cuentasapi.auth.CurrentUser;
import com.cuentasapi.transfer.TransferService.TransferRequest;
import com.cuentasapi.transfer.TransferService.TransferResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse create(@RequestBody TransferRequest request) {
        return transfers.transfer(CurrentUser.id(), request);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable long id) {
        return transfers.get(id, CurrentUser.id(), CurrentUser.isOperator());
    }
}
