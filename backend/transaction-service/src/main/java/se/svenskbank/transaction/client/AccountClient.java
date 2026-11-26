package se.svenskbank.transaction.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import se.svenskbank.transaction.exception.AccountServiceUnavailableException;
import se.svenskbank.transaction.exception.BankOperationException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * REST client for account-service. This is the only place that talks to the other microservice.
 */
@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);

    private final RestClient restClient;

    public AccountClient(@Value("${account-service.url}") String accountServiceUrl) {
        // Timeouts so a slow account-service cannot block this service forever.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(accountServiceUrl)
                .requestFactory(factory)
                .build();
    }

    /** Positive amount = money in, negative amount = money out. */
    public void updateBalance(Long accountId, BigDecimal amount) {
        log.info("Calling account-service: PUT /api/accounts/{}/balance amount={}", accountId, amount);
        try {
            restClient.put()
                    .uri("/api/accounts/{id}/balance", accountId)
                    .body(new BalanceUpdateRequest(amount))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            // 4xx from account-service, e.g. not found / insufficient funds
            log.warn("account-service rejected the request: HTTP {}", ex.getStatusCode().value());
            throw new BankOperationException(readError(ex));
        } catch (RestClientException ex) {
            // connection refused, timeout or 5xx
            log.error("account-service is unavailable: {}", ex.getMessage());
            throw new AccountServiceUnavailableException("Account service is unavailable, please try again later", ex);
        }
    }

    private String readError(HttpClientErrorException ex) {
        try {
            Map<?, ?> body = ex.getResponseBodyAs(Map.class);
            if (body != null && body.get("error") != null) {
                return body.get("error").toString();
            }
        } catch (RuntimeException ignored) {
            // body was not JSON, fall through
        }
        return "Request to account service failed";
    }

    private record BalanceUpdateRequest(BigDecimal amount) {
    }
}
