package com.snapnbbang.service;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 화면·DB와 무관한 순수 정산 계산기.
 *
 * [정산 계산 규칙]
 * 1. 각 메뉴의 먹은 사람 = 전체 참여자 - 그 차수 불참자 - 그 메뉴 제외자 (0명이면 IllegalArgumentException, 메뉴명 포함)
 * 2. 메뉴 금액을 먹은 사람 수로 나눠 각자의 차수별 원시 금액에 더한다.
 * 3. 차수의 adjustmentAmount는 그 차수의 원시 금액 비율대로 사람마다 배분한다.
 *    (그 차수 메뉴 합계가 0이면 배분하지 않고 그대로 합계에만 더한다)
 * 4. 사람별로 모든 차수 금액을 합산한 뒤 내림(floor)은 한 번만 한다.
 * 5. 총무가 아닌 사람의 금액 = 내림한 값. 총무 금액 = grandTotal - 다른 참여자 내림 합계.
 *
 * 부동소수점(double) 및 중간 반올림 오차를 완전히 배제하기 위해 BigInteger 기반 기약분수(Fraction)로 정확히 연산한다.
 */
@Component
public class SettlementCalculator {

    public CalculationResult calculate(List<ParticipantInput> participants, List<RoundInput> rounds) {
        validateParticipants(participants);
        if (rounds == null) {
            throw new IllegalArgumentException("차수 목록(rounds)은 null일 수 없습니다.");
        }

        Map<String, Fraction> totalRawByParticipant = new LinkedHashMap<>();
        String hostName = null;
        for (ParticipantInput participant : participants) {
            totalRawByParticipant.put(participant.name(), Fraction.ZERO);
            if (participant.isHost()) {
                hostName = participant.name();
            }
        }

        long grandTotal = 0L;

        for (RoundInput round : rounds) {
            if (round == null) {
                throw new IllegalArgumentException("차수 정보는 null일 수 없습니다.");
            }

            Set<String> absentSet = toTrimmedSet(round.absentNames());
            List<ItemInput> items = round.items() == null ? Collections.emptyList() : round.items();

            Map<String, Fraction> roundRawByParticipant = new LinkedHashMap<>();
            for (ParticipantInput participant : participants) {
                roundRawByParticipant.put(participant.name(), Fraction.ZERO);
            }

            long roundMenuSum = 0L;

            for (ItemInput item : items) {
                if (item == null || item.name() == null || item.name().isBlank()) {
                    throw new IllegalArgumentException("메뉴 이름이 비어 있습니다.");
                }
                if (item.price() < 0) {
                    throw new IllegalArgumentException("메뉴 가격은 0 이상이어야 합니다: " + item.name());
                }

                Set<String> excludedSet = toTrimmedSet(item.excludedNames());
                List<String> eaters = participants.stream()
                        .map(ParticipantInput::name)
                        .filter(name -> !absentSet.contains(name) && !excludedSet.contains(name))
                        .toList();

                if (eaters.isEmpty()) {
                    throw new IllegalArgumentException(
                            "먹은 사람이 한 명도 없는 메뉴가 있습니다: " + item.name());
                }

                roundMenuSum = Math.addExact(roundMenuSum, item.price());

                if (item.price() > 0) {
                    Fraction sharePerEater = Fraction.of(item.price(), eaters.size());
                    for (String eater : eaters) {
                        roundRawByParticipant.put(
                                eater,
                                roundRawByParticipant.get(eater).add(sharePerEater)
                        );
                    }
                }
            }

            long adjustment = round.adjustmentAmount();
            long roundTotal = Math.addExact(roundMenuSum, adjustment);
            if (roundTotal < 0) {
                throw new IllegalArgumentException(
                        round.roundNumber() + "차 합계(메뉴 합계 + 가감액)는 0 이상이어야 합니다.");
            }
            grandTotal = Math.addExact(grandTotal, roundTotal);

            // 차수 메뉴 합계가 0보다 크면 원시 금액 비율로 adjustmentAmount를 배분한다.
            // 즉, participantRoundTotal = raw + raw * (adjustment / roundMenuSum)
            if (roundMenuSum > 0) {
                Fraction adjustmentRatio = Fraction.of(adjustment, roundMenuSum);
                for (ParticipantInput participant : participants) {
                    String name = participant.name();
                    Fraction raw = roundRawByParticipant.get(name);
                    if (!raw.isZero()) {
                        Fraction adjustedShare = raw.add(raw.multiply(adjustmentRatio));
                        totalRawByParticipant.put(name, totalRawByParticipant.get(name).add(adjustedShare));
                    }
                }
            }
        }

        Map<String, Long> finalAmounts = new LinkedHashMap<>();
        long nonHostFlooredSum = 0L;

        for (ParticipantInput participant : participants) {
            String name = participant.name();
            if (name.equals(hostName)) {
                continue;
            }
            long floored = totalRawByParticipant.get(name).floor();
            finalAmounts.put(name, floored);
            nonHostFlooredSum = Math.addExact(nonHostFlooredSum, floored);
        }

        long hostAmount = Math.subtractExact(grandTotal, nonHostFlooredSum);
        finalAmounts.put(hostName, hostAmount);

        // 입력 참여자 순서 유지하여 결과 맵 구성
        Map<String, Long> orderedAmounts = new LinkedHashMap<>();
        for (ParticipantInput participant : participants) {
            orderedAmounts.put(participant.name(), finalAmounts.get(participant.name()));
        }

        return new CalculationResult(Collections.unmodifiableMap(orderedAmounts), grandTotal);
    }

    private void validateParticipants(List<ParticipantInput> participants) {
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("참여자는 최소 1명 이상이어야 합니다.");
        }

        Set<String> seenNames = new HashSet<>();
        int hostCount = 0;

        for (ParticipantInput p : participants) {
            if (p == null || p.name() == null || p.name().isBlank()) {
                throw new IllegalArgumentException("참여자 이름은 비어 있을 수 없습니다.");
            }
            if (!seenNames.add(p.name())) {
                throw new IllegalArgumentException("중복된 참여자 이름이 있습니다: " + p.name());
            }
            if (p.isHost()) {
                hostCount++;
            }
        }

        if (hostCount != 1) {
            throw new IllegalArgumentException("총무는 정확히 1명이어야 합니다. 현재 총무 수: " + hostCount);
        }
    }

    private Set<String> toTrimmedSet(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                set.add(name);
            }
        }
        return set;
    }

    public record ParticipantInput(String name, boolean isHost) {
    }

    public record RoundInput(
            int roundNumber,
            long adjustmentAmount,
            List<String> absentNames,
            List<ItemInput> items) {

        public RoundInput(int roundNumber, long adjustmentAmount, List<ItemInput> items) {
            this(roundNumber, adjustmentAmount, Collections.emptyList(), items);
        }
    }

    public record ItemInput(String name, long price, List<String> excludedNames) {

        public ItemInput(String name, long price) {
            this(name, price, Collections.emptyList());
        }
    }

    public record CalculationResult(Map<String, Long> amounts, long grandTotal) {

        public Map<String, Long> getAmounts() {
            return amounts;
        }

        public long getGrandTotal() {
            return grandTotal;
        }
    }

    /**
     * BigInteger 기반 정확한 유리수(기약분수) 표현.
     * 분모(den)는 항상 양수(> 0)로 유지된다.
     */
    private static final class Fraction {

        static final Fraction ZERO = new Fraction(BigInteger.ZERO, BigInteger.ONE);

        private final BigInteger num;
        private final BigInteger den;

        private Fraction(BigInteger num, BigInteger den) {
            if (den.signum() == 0) {
                throw new ArithmeticException("분모는 0일 수 없습니다.");
            }
            if (num.signum() == 0) {
                this.num = BigInteger.ZERO;
                this.den = BigInteger.ONE;
                return;
            }
            if (den.signum() < 0) {
                num = num.negate();
                den = den.negate();
            }
            BigInteger gcd = num.abs().gcd(den);
            this.num = num.divide(gcd);
            this.den = den.divide(gcd);
        }

        static Fraction of(long numerator, long denominator) {
            return new Fraction(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
        }

        boolean isZero() {
            return num.signum() == 0;
        }

        Fraction add(Fraction other) {
            Objects.requireNonNull(other);
            if (this.isZero()) {
                return other;
            }
            if (other.isZero()) {
                return this;
            }
            BigInteger nextNum = this.num.multiply(other.den).add(other.num.multiply(this.den));
            BigInteger nextDen = this.den.multiply(other.den);
            return new Fraction(nextNum, nextDen);
        }

        Fraction multiply(Fraction other) {
            Objects.requireNonNull(other);
            if (this.isZero() || other.isZero()) {
                return ZERO;
            }
            return new Fraction(this.num.multiply(other.num), this.den.multiply(other.den));
        }

        long floor() {
            BigInteger[] qr = num.divideAndRemainder(den);
            BigInteger quotient = qr[0];
            BigInteger remainder = qr[1];
            if (remainder.signum() < 0) {
                quotient = quotient.subtract(BigInteger.ONE);
            }
            return quotient.longValueExact();
        }
    }
}
