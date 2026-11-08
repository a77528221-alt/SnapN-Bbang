package com.snapnbbang.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snapnbbang.service.SettlementCalculator.CalculationResult;
import com.snapnbbang.service.SettlementCalculator.ItemInput;
import com.snapnbbang.service.SettlementCalculator.ParticipantInput;
import com.snapnbbang.service.SettlementCalculator.RoundInput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    private final SettlementCalculator calculator = new SettlementCalculator();

    @Test
    @DisplayName("기본 예시: 1차 삼겹살·소주(영희 제외), 2차 먹태·맥주(지민 불참) 정산 검증")
    void calculate_defaultScenario() {
        List<ParticipantInput> participants = List.of(
                new ParticipantInput("민수", true),
                new ParticipantInput("철수", false),
                new ParticipantInput("영희", false),
                new ParticipantInput("지민", false)
        );

        List<RoundInput> rounds = List.of(
                new RoundInput(
                        1,
                        0L,
                        List.of(),
                        List.of(
                                new ItemInput("삼겹살", 40_000L, List.of()),
                                new ItemInput("소주", 10_000L, List.of("영희"))
                        )
                ),
                new RoundInput(
                        2,
                        0L,
                        List.of("지민"),
                        List.of(
                                new ItemInput("먹태", 15_000L, List.of()),
                                new ItemInput("맥주", 12_000L, List.of())
                        )
                )
        );

        CalculationResult result = calculator.calculate(participants, rounds);

        assertThat(result.grandTotal()).isEqualTo(77_000L);
        assertThat(result.amounts().get("철수")).isEqualTo(22_333L);
        assertThat(result.amounts().get("영희")).isEqualTo(19_000L);
        assertThat(result.amounts().get("지민")).isEqualTo(13_333L);
        assertThat(result.amounts().get("민수")).isEqualTo(22_334L);

        long sum = result.amounts().values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(result.grandTotal());
    }

    @Test
    @DisplayName("먹은 사람이 한 명도 없는 메뉴가 있으면 어느 메뉴인지 포함한 IllegalArgumentException이 발생한다")
    void calculate_zeroEaterMenu_throwsIllegalArgumentExceptionWithMenuName() {
        List<ParticipantInput> participants = List.of(
                new ParticipantInput("민수", true),
                new ParticipantInput("철수", false),
                new ParticipantInput("지민", false)
        );

        // 지민은 1차 불참, 민수·철수는 '매운닭발' 메뉴에서 제외 -> 먹은 사람 0명
        List<RoundInput> rounds = List.of(
                new RoundInput(
                        1,
                        0L,
                        List.of("지민"),
                        List.of(
                                new ItemInput("삼겹살", 30_000L, List.of()),
                                new ItemInput("매운닭발", 18_000L, List.of("민수", "철수"))
                        )
                )
        );

        assertThatThrownBy(() -> calculator.calculate(participants, rounds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("매운닭발");
    }

    @Test
    @DisplayName("할인(음수 adjustmentAmount)이 있는 차수에서 원시 금액 비율대로 할인이 배분된다")
    void calculate_negativeAdjustmentDiscount() {
        List<ParticipantInput> participants = List.of(
                new ParticipantInput("민수", true),
                new ParticipantInput("철수", false),
                new ParticipantInput("영희", false)
        );

        // 메뉴 합계: 스테이크 30,000원(민수·철수 2명 -> 각 15,000원), 파스타 10,000원(영희 단독 -> 10,000원)
        // 원시 금액 비율: 민수 3/8, 철수 3/8, 영희 2/8 (메뉴 합계 40,000원)
        // 할인 -4,000원 적용 시: 민수 -1,500원(13,500), 철수 -1,500원(13,500), 영희 -1,000원(9,000), 합계 36,000원
        List<RoundInput> rounds = List.of(
                new RoundInput(
                        1,
                        -4_000L,
                        List.of(),
                        List.of(
                                new ItemInput("스테이크", 30_000L, List.of("영희")),
                                new ItemInput("파스타", 10_000L, List.of("민수", "철수"))
                        )
                )
        );

        CalculationResult result = calculator.calculate(participants, rounds);

        assertThat(result.grandTotal()).isEqualTo(36_000L);
        assertThat(result.amounts().get("철수")).isEqualTo(13_500L);
        assertThat(result.amounts().get("영희")).isEqualTo(9_000L);
        assertThat(result.amounts().get("민수")).isEqualTo(13_500L);
    }

    @Test
    @DisplayName("총무가 모든 메뉴에서 제외된 경우에도 총무 금액은 항상 0 이상이다")
    void calculate_hostExcludedFromAllMenus_hostAmountIsNonNegative() {
        List<ParticipantInput> participants = List.of(
                new ParticipantInput("민수", true),
                new ParticipantInput("철수", false),
                new ParticipantInput("영희", false),
                new ParticipantInput("지민", false)
        );

        // 1) 3명이 1,000원짜리 메뉴 3개를 나눠 먹어 정확히 1,000원씩 떨어지는 경우 (중간 반올림 오차 없이 총무 0원)
        List<RoundInput> exactRounds = List.of(
                new RoundInput(
                        1,
                        0L,
                        List.of(),
                        List.of(
                                new ItemInput("메뉴A", 1_000L, List.of("민수")),
                                new ItemInput("메뉴B", 1_000L, List.of("민수")),
                                new ItemInput("메뉴C", 1_000L, List.of("민수"))
                        )
                )
        );
        CalculationResult exactResult = calculator.calculate(participants, exactRounds);
        assertThat(exactResult.grandTotal()).isEqualTo(3_000L);
        assertThat(exactResult.amounts().get("철수")).isEqualTo(1_000L);
        assertThat(exactResult.amounts().get("영희")).isEqualTo(1_000L);
        assertThat(exactResult.amounts().get("지민")).isEqualTo(1_000L);
        assertThat(exactResult.amounts().get("민수")).isEqualTo(0L);

        // 2) 3명이 10,000원을 나눠 먹어 각자 3,333원 내림되고 자투리 1원이 남는 경우 (총무 1원 >= 0)
        List<RoundInput> remainderRounds = List.of(
                new RoundInput(
                        1,
                        0L,
                        List.of(),
                        List.of(new ItemInput("안주", 10_000L, List.of("민수")))
                )
        );
        CalculationResult remainderResult = calculator.calculate(participants, remainderRounds);
        assertThat(remainderResult.grandTotal()).isEqualTo(10_000L);
        assertThat(remainderResult.amounts().get("민수")).isGreaterThanOrEqualTo(0L);
        assertThat(remainderResult.amounts().get("민수")).isEqualTo(1L);
    }

    @Test
    @DisplayName("무작위 입력 100개에 대해 '모든 참여자 금액 합 = grandTotal'과 '총무 금액 >= 0'이 항상 성립한다")
    void calculate_100RandomInputs_invariantsAlwaysHold() {
        Random random = new Random(20261108L);

        for (int trial = 0; trial < 100; trial++) {
            int participantCount = 2 + random.nextInt(7); // 2 ~ 8명
            int hostIdx = random.nextInt(participantCount);
            List<ParticipantInput> participants = new ArrayList<>(participantCount);
            List<String> allNames = new ArrayList<>(participantCount);

            for (int p = 0; p < participantCount; p++) {
                String name = "참여자" + p;
                participants.add(new ParticipantInput(name, p == hostIdx));
                allNames.add(name);
            }
            String hostName = allNames.get(hostIdx);

            int roundCount = 1 + random.nextInt(4); // 1 ~ 4차
            List<RoundInput> rounds = new ArrayList<>(roundCount);

            for (int r = 1; r <= roundCount; r++) {
                // 최소 1명은 해당 차수에 참석하도록 불참자 무작위 선정
                List<String> shuffledForAbsence = new ArrayList<>(allNames);
                Collections.shuffle(shuffledForAbsence, random);
                int absentCount = random.nextInt(participantCount); // 0 ~ participantCount-1
                List<String> absentNames = new ArrayList<>(shuffledForAbsence.subList(0, absentCount));
                List<String> activeInRound = shuffledForAbsence.subList(absentCount, participantCount);

                int itemCount = 1 + random.nextInt(6); // 1 ~ 6개 메뉴
                List<ItemInput> items = new ArrayList<>(itemCount);
                long roundMenuSum = 0L;

                for (int i = 1; i <= itemCount; i++) {
                    // 최소 1명은 해당 메뉴를 먹도록 제외자 선정
                    List<String> shuffledActive = new ArrayList<>(activeInRound);
                    Collections.shuffle(shuffledActive, random);
                    int excludeCount = random.nextInt(shuffledActive.size()); // 0 ~ active-1
                    List<String> excludedNames = new ArrayList<>(shuffledActive.subList(0, excludeCount));

                    long price = random.nextInt(100_001); // 0 ~ 100,000원
                    roundMenuSum += price;
                    items.add(new ItemInput("메뉴-" + r + "-" + i, price, excludedNames));
                }

                // 가감액: -roundMenuSum/2 ~ +30,000원 (차수 총액 >= 0 보장)
                long minAdjustment = -(roundMenuSum / 2);
                long maxAdjustment = 30_000L;
                long adjustment = minAdjustment
                        + (long) (random.nextDouble() * (maxAdjustment - minAdjustment + 1));

                rounds.add(new RoundInput(r, adjustment, absentNames, items));
            }

            CalculationResult result = calculator.calculate(participants, rounds);

            long sumOfAmounts = result.amounts().values().stream().mapToLong(Long::longValue).sum();
            long hostAmount = result.amounts().get(hostName);

            assertThat(sumOfAmounts)
                    .as("Trial %d: 모든 참여자 금액 합 == grandTotal", trial)
                    .isEqualTo(result.grandTotal());
            assertThat(hostAmount)
                    .as("Trial %d: 총무 금액 >= 0", trial)
                    .isGreaterThanOrEqualTo(0L);
        }
    }
}
