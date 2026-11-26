package com.dineflow.dto.payment;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SepayWebhookRequest {
    private Long id;
    private String gateway;

    @JsonAlias({"transactionDate", "transaction_date"})
    private String transactionDate;

    @JsonAlias({"accountNumber", "account_number"})
    private String accountNumber;

    private String code;
    private String content;

    @JsonAlias({"transferType", "transfer_type"})
    private String transferType; // "in" là tiền vào, "out" là tiền ra

    @JsonAlias({"transferAmount", "transfer_amount"})
    private BigDecimal transferAmount;

    private BigDecimal accumulated;

    @JsonAlias({"subAccount", "sub_account"})
    private String subAccount;

    @JsonAlias({"referenceCode", "reference_code"})
    private String referenceCode;

    private String description;
}