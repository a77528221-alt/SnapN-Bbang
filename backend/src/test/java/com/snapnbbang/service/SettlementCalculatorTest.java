package com.snapnbbang.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    private final SettlementCalculator calculator = new SettlementCalculator();

    @Test
    @DisplayName("차수별 메뉴 제외·불참·가감액 비율 배분 및 총무 잔액 계산 검증 뼈대")
    void calculate_skeleton() {
        // TODO: 대표 시나리오(삼겹살 40,000원, 소주 10,000원, 2차 먹태 15,000원·맥주 12,000원 등) 단위 테스트 작성
        calculator.calculate();
    }
}

