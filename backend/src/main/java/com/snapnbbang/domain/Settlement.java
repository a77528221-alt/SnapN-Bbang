package com.snapnbbang.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Settlement {

    private String id;
    private String title;
    private LocalDate settledOn;
    private String status;

    @ToString.Exclude
    private String adminTokenHash;

    private String bank;

    @ToString.Exclude
    private String accountNumber;

    private String accountHolder;

    @ToString.Exclude
    private String payLink;

    private LocalDateTime createdAt;
    private LocalDateTime sharedAt;
    private LocalDateTime accountPurgeAt;
    private LocalDateTime expiresAt;
}

