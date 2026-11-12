package com.snapnbbang.domain;

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
public class SettlementParticipant {

    private Long id;
    private String settlementId;
    private String name;
    private Boolean isHost;

    @ToString.Exclude
    private String tokenHash;

    private LocalDateTime claimedAt;
    private String payStatus;
    private LocalDateTime reportedAt;
    private LocalDateTime confirmedAt;

    public boolean isHost() {
        return Boolean.TRUE.equals(isHost);
    }
}
