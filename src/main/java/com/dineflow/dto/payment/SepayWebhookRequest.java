package com.dineflow.dto.payment;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SepayWebhookRequest {
    private Long id;
    private String gateway;
    private String transactionDate;
    private String accountNumber;
    private String code;
    private String content;
    private String transferType; // "in" là tiền vào, "out" là tiền ra
    private BigDecimal transferAmount;
    private BigDecimal accumulated;
    private String subAccount;
    private String referenceCode;
    private String description;
}
