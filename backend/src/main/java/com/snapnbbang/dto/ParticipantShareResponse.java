package com.snapnbbang.dto;

import java.util.List;
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
public class ParticipantShareResponse {

    private String name;
    private long amount;
    private List<ShareItem> items;
    private List<String> excludedItems;
    private List<Integer> absentRounds;

    @ToString.Exclude
    private AccountInfo account;

    @ToString.Exclude
    private String payLink;

    private String payStatus;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ShareItem {
        private int roundNumber;
        private String placeName;
        private String name;
        /**
         * 항목별 표시용 부담 금액(원 미만 내림).
         * 최종 정산 금액(amount)은 차수별 가감액 배분 및 전 차수 합산 후 1회 내림 규칙으로 계산되므로,
         * 항목별 share 합계와 amount 사이에 몇 원 차이가 날 수 있다.
         */
        private long share;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class AccountInfo {
        private String bank;

        @ToString.Exclude
        private String accountNumber;

        private String accountHolder;
    }
}
