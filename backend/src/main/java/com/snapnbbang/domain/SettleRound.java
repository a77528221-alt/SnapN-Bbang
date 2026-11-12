package com.snapnbbang.domain;

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
public class SettleRound {

    private Long id;
    private String settlementId;
    private Integer roundNumber;
    private String placeName;
    private Integer receiptTotal;
    private Integer adjustmentAmount;
}
