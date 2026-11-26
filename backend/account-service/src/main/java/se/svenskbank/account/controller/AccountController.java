package se.svenskbank.account.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.svenskbank.account.dto.AccountResponse;
import se.svenskbank.account.dto.BalanceUpdateRequest;
import se.svenskbank.account.dto.CreateAccountRequest;
import se.svenskbank.account.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> getAll() {
        return accountService.findAll();
    }

    @GetMapping("/{id}")
    public AccountResponse getById(@PathVariable Long id) {
        return accountService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(request);
    }

    /** Internal endpoint: used by transaction-service (blocked from the outside by the frontend proxy). */
    @PutMapping("/{id}/balance")
    public AccountResponse updateBalance(@PathVariable Long id, @Valid @RequestBody BalanceUpdateRequest request) {
        return accountService.updateBalance(id, request.amount());
    }
}
