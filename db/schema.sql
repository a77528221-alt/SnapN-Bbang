-- ============================================================
-- 스냅 N빵 (Snap N-bbang) - MySQL 8.0.16+ 스키마 (1차 출시 버전)
-- 문자셋: utf8mb4 (한글 이름·메뉴명 대응)
--
-- 구조:  settlement(정산) 1 - N settle_round(차수) 1 - N item(메뉴)
--        settlement 1 - N settlement_participant(정산 참여자, 총무 포함)
--
-- 1차 버전에는 서버 "모임" 개념이 없다. 정산이 최상위이며 링크 하나가 정산 하나다.
-- 모임·명단 저장은 총무 기기(localStorage)에서만 처리하고,
-- 서버 모임 구조는 2차 업데이트에서 settlement 위에 추가한다.
-- 정산을 삭제하면 하위 데이터가 ON DELETE CASCADE 로 모두 함께 삭제된다.
-- ============================================================

CREATE DATABASE IF NOT EXISTS snap_nbbang
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE snap_nbbang;

-- ------------------------------------------------------------
-- 1. 정산 (한 번의 자리). 링크 하나가 정산 하나에 해당한다.
--    id: 공유 링크에 들어가는 랜덤 값(nanoid 21자 등). 순차 번호 사용 금지.
--    admin_token_hash: 총무 토큰의 SHA-256 해시 (원문 저장 금지)
--    status: DRAFT(작성 중) -> SHARED(링크 공유됨) -> CLOSED(마감)
--            1차 버전은 링크 생성 시 곧바로 SHARED 로 저장한다.
--    account_purge_at: 이 시각이 지나면 계좌 정보만 삭제한다. (공유 후 90일)
--    expires_at: 이 시각이 지나면 정산 전체를 삭제한다. (생성 후 12개월)
-- ------------------------------------------------------------
CREATE TABLE settlement (
  id                VARCHAR(21)  NOT NULL,
  title             VARCHAR(60)  NOT NULL,
  settled_on        DATE         NOT NULL,
  status            ENUM('DRAFT','SHARED','CLOSED') NOT NULL DEFAULT 'SHARED',
  admin_token_hash  CHAR(64)     NOT NULL,
  bank              VARCHAR(30)  NULL,
  account_number    VARCHAR(40)  NULL,
  account_holder    VARCHAR(30)  NULL,
  pay_link          VARCHAR(255) NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  shared_at         DATETIME     NULL,
  account_purge_at  DATETIME     NULL,
  expires_at        DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_settlement_purge (account_purge_at),
  KEY idx_settlement_expires (expires_at)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 2. 정산 참여자 (총무 포함)
--    token_hash: 이름 점유 시 발급한 참여자 토큰의 해시. NULL이면 아직 점유되지 않은 이름.
--    pay_status: UNPAID(미입금) -> REPORTED(송금 신고, 참여자) -> CONFIRMED(입금 확인, 총무)
--    is_host: 총무. 한 정산에 한 명만 가능하며 송금하지 않으므로 항상 CONFIRMED.
-- ------------------------------------------------------------
CREATE TABLE settlement_participant (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  settlement_id  VARCHAR(21) NOT NULL,
  name           VARCHAR(30) NOT NULL,
  is_host        TINYINT(1)  NOT NULL DEFAULT 0,
  host_marker    TINYINT GENERATED ALWAYS AS (CASE WHEN is_host = 1 THEN 1 ELSE NULL END) STORED,
  token_hash     CHAR(64)    NULL,
  claimed_at     DATETIME    NULL,
  pay_status     ENUM('UNPAID','REPORTED','CONFIRMED') NOT NULL DEFAULT 'UNPAID',
  reported_at    DATETIME    NULL,
  confirmed_at   DATETIME    NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_participant_name (settlement_id, name),
  UNIQUE KEY uq_one_host (settlement_id, host_marker),
  CONSTRAINT chk_host_confirmed CHECK (is_host = 0 OR pay_status = 'CONFIRMED'),
  CONSTRAINT fk_participant_settlement
    FOREIGN KEY (settlement_id) REFERENCES settlement (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 3. 차수 (1차, 2차, ...)
--    receipt_total: 영수증 총액 (AI 파싱 합계 검증용)
--    adjustment_amount: 부가세·봉사료·할인 등 메뉴 합계 외 가감액 (할인은 음수)
-- ------------------------------------------------------------
CREATE TABLE settle_round (
  id                 BIGINT      NOT NULL AUTO_INCREMENT,
  settlement_id      VARCHAR(21) NOT NULL,
  round_number       INT         NOT NULL,
  place_name         VARCHAR(60) NULL,
  receipt_total      INT         NULL,
  adjustment_amount  INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_round_number (settlement_id, round_number),
  CONSTRAINT fk_round_settlement
    FOREIGN KEY (settlement_id) REFERENCES settlement (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 4. 차수 불참자 (해당 차수에 아예 참석하지 않은 사람)
-- ------------------------------------------------------------
CREATE TABLE round_absence (
  round_id        BIGINT NOT NULL,
  participant_id  BIGINT NOT NULL,
  PRIMARY KEY (round_id, participant_id),
  CONSTRAINT fk_absence_round
    FOREIGN KEY (round_id) REFERENCES settle_round (id) ON DELETE CASCADE,
  CONSTRAINT fk_absence_participant
    FOREIGN KEY (participant_id) REFERENCES settlement_participant (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 5. 메뉴
--    price: 해당 줄의 총액 (예: 삼겹살 3인분 45000)
-- ------------------------------------------------------------
CREATE TABLE item (
  id        BIGINT      NOT NULL AUTO_INCREMENT,
  round_id  BIGINT      NOT NULL,
  name      VARCHAR(60) NOT NULL,
  qty       INT         NOT NULL DEFAULT 1,
  price     INT         NOT NULL,
  PRIMARY KEY (id),
  KEY idx_item_round (round_id),
  CONSTRAINT chk_item_price CHECK (price >= 0),
  CONSTRAINT fk_item_round
    FOREIGN KEY (round_id) REFERENCES settle_round (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 6. 메뉴 제외 ("이 메뉴는 이 사람이 먹지 않음")
--    기본값은 전원 참여이므로, 제외된 경우에만 행이 생긴다.
-- ------------------------------------------------------------
CREATE TABLE item_exclusion (
  item_id         BIGINT NOT NULL,
  participant_id  BIGINT NOT NULL,
  PRIMARY KEY (item_id, participant_id),
  CONSTRAINT fk_exclusion_item
    FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE CASCADE,
  CONSTRAINT fk_exclusion_participant
    FOREIGN KEY (participant_id) REFERENCES settlement_participant (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 뷰: 각 메뉴를 실제로 먹은 사람 (불참자·제외자 제거)
-- ============================================================
CREATE VIEW v_item_eligible AS
SELECT i.id            AS item_id,
       p.id            AS participant_id,
       r.settlement_id AS settlement_id
FROM item i
JOIN settle_round r             ON r.id = i.round_id
JOIN settlement_participant p   ON p.settlement_id = r.settlement_id
LEFT JOIN round_absence a       ON a.round_id = r.id AND a.participant_id = p.id
LEFT JOIN item_exclusion e      ON e.item_id = i.id  AND e.participant_id = p.id
WHERE a.participant_id IS NULL
  AND e.participant_id IS NULL;

-- ============================================================
-- 참고 쿼리 (MyBatis 매퍼에서는 :값 대신 #{값} 바인딩을 사용한다. ${} 금지)
-- ============================================================

-- [1] 참여자별 원시 부담금 (내림·가감액 배분 전)
--     부가세·봉사료 배분, 참여자별 1회 내림, 총무 잔액 계산은 서버(Java)에서 수행한다.
-- SELECT v.participant_id,
--        SUM(i.price / c.cnt) AS raw_due
-- FROM v_item_eligible v
-- JOIN item i ON i.id = v.item_id
-- JOIN (SELECT item_id, COUNT(*) AS cnt FROM v_item_eligible GROUP BY item_id) c
--   ON c.item_id = v.item_id
-- WHERE v.settlement_id = :settlementId
-- GROUP BY v.participant_id;

-- [2] 이름 점유 (동시에 같은 이름을 눌러도 한 명만 성공: 영향 행 수가 1이면 성공)
-- UPDATE settlement_participant
-- SET token_hash = :tokenHash, claimed_at = NOW()
-- WHERE id = :participantId AND settlement_id = :settlementId AND token_hash IS NULL;

-- [3] 점유 해제 (총무가 잘못 고른 이름을 풀어 줌)
-- UPDATE settlement_participant
-- SET token_hash = NULL, claimed_at = NULL, pay_status = 'UNPAID',
--     reported_at = NULL, confirmed_at = NULL
-- WHERE id = :participantId AND settlement_id = :settlementId AND is_host = 0;

-- [4] 송금 신고 (본인 토큰 + 미입금 상태에서만)
-- UPDATE settlement_participant
-- SET pay_status = 'REPORTED', reported_at = NOW()
-- WHERE id = :participantId AND settlement_id = :settlementId
--   AND token_hash = :tokenHash AND pay_status = 'UNPAID';

-- [5] 송금 신고 취소 (본인 토큰 + 신고 상태에서만)
-- UPDATE settlement_participant
-- SET pay_status = 'UNPAID', reported_at = NULL
-- WHERE id = :participantId AND settlement_id = :settlementId
--   AND token_hash = :tokenHash AND pay_status = 'REPORTED';

-- [6] 입금 확인 (총무 토큰 검증은 서비스 계층에서 settlement.admin_token_hash 와 비교한 뒤 실행)
-- UPDATE settlement_participant
-- SET pay_status = 'CONFIRMED', confirmed_at = NOW()
-- WHERE id = :participantId AND settlement_id = :settlementId
--   AND pay_status = 'REPORTED';

-- ============================================================
-- 정리 작업 (Spring @Scheduled 로 하루 1회 실행)
-- ============================================================

-- [A] 계좌 정보만 삭제: 링크 공유 90일 뒤 (공유 시점에 account_purge_at = NOW() + 90일 로 설정)
-- UPDATE settlement
-- SET bank = NULL, account_number = NULL, account_holder = NULL,
--     pay_link = NULL, account_purge_at = NULL
-- WHERE account_purge_at IS NOT NULL AND account_purge_at < NOW();

-- [B] 만료된 정산 전체 삭제 (생성 후 12개월). CASCADE 로 하위 데이터가 함께 삭제된다.
--     연락처가 없어 사전 알림이 어려우므로 앱 안에서 "12개월 뒤 삭제돼요"를 고지한다.
-- DELETE FROM settlement WHERE expires_at < NOW();

-- [C] 총무가 정산을 직접 삭제할 때
-- DELETE FROM settlement WHERE id = :settlementId;
