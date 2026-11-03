package com.snapnbbang.service;

import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SettlementCalculator {

    /**
     * 참여자별 최종 정산 금액을 계산한다.
     * - 각 메뉴 금액을 먹은 인원(차수 불참자·메뉴 제외자 제외)으로 나눈다.
     * - 부가세·봉사료·할인은 차수 내 메뉴 금액 비율로 배분한다.
     * - 내림은 참여자별 합계에서 1회만 수행한다.
     * - 총무 부담금 = 영수증 합계 - 다른 참여자 내림 합계.
     */
    public Map<Long, Integer> calculate() {
        // TODO: 정산 계산 로직 구현
        return Collections.emptyMap();
    }
}

