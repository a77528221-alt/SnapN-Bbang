/**
 * 정산 계산 순수 함수 자리 (src/lib/calc.js)
 *
 * [정산 계산 규칙]
 * - 각 메뉴 금액을 그 메뉴를 먹은 인원(차수 불참자, 메뉴 제외자 제외)으로 나눈다. 총무도 인원에 포함된다.
 * - 부가세·봉사료·할인은 차수 안에서 메뉴 금액 비율로 배분한다.
 * - 내림은 참여자별 합계에서 한 번만 한다.
 * - 총무 부담금 = 영수증 합계 - 다른 참여자 내림 합계.
 *
 * TODO: 본 계산 로직 구현 단계에서 Java 서비스 계층과 동일한 규칙으로 정밀 검증 예정.
 * 현재 뼈대 단계에서는 화면 미리보기용 계산만 수행한다.
 */
export function calculateSettlementPreview({ participants = [], rounds = [] }) {
  const rawByParticipantId = {};
  participants.forEach((p) => {
    rawByParticipantId[p.id] = 0;
  });

  let grandTotal = 0;
  const zeroEaterItemIds = [];

  rounds.forEach((round) => {
    const absentSet = new Set(round.absentParticipantIds || []);
    const activeParticipants = participants.filter((p) => !absentSet.has(p.id));

    const roundRawMap = {};
    activeParticipants.forEach((p) => {
      roundRawMap[p.id] = 0;
    });

    let roundItemsSum = 0;

    (round.items || []).forEach((item) => {
      const price = Number(item.price) || 0;
      roundItemsSum += price;

      const excludedSet = new Set(item.excludedParticipantIds || []);
      const eaters = activeParticipants.filter((p) => !excludedSet.has(p.id));

      if (eaters.length === 0) {
        zeroEaterItemIds.push(item.id);
        return;
      }

      const share = price / eaters.length;
      eaters.forEach((p) => {
        roundRawMap[p.id] += share;
      });
    });

    const adjustment = Number(round.adjustmentAmount) || 0;
    const roundTotal = roundItemsSum + adjustment;
    grandTotal += roundTotal;

    if (roundItemsSum > 0 && adjustment !== 0) {
      activeParticipants.forEach((p) => {
        const ratio = roundRawMap[p.id] / roundItemsSum;
        roundRawMap[p.id] += adjustment * ratio;
      });
    }

    activeParticipants.forEach((p) => {
      rawByParticipantId[p.id] += roundRawMap[p.id];
    });
  });

  const host = participants.find((p) => p.isHost) || participants[0];
  let nonHostFlooredSum = 0;
  const amountsByParticipantId = {};

  participants.forEach((p) => {
    if (host && p.id === host.id) return;
    const floored = Math.floor(rawByParticipantId[p.id] || 0);
    amountsByParticipantId[p.id] = floored;
    nonHostFlooredSum += floored;
  });

  if (host) {
    amountsByParticipantId[host.id] = grandTotal - nonHostFlooredSum;
  }

  return {
    grandTotal,
    amountsByParticipantId,
    zeroEaterItemIds,
  };
}

