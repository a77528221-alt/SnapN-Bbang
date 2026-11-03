import { useState } from 'react';
import { useParams } from 'react-router-dom';
import AppShell from '../components/AppShell';
import PaymentSheet from '../components/PaymentSheet';
import { storage } from '../lib/storage';
import { MOCK_SETTLEMENT_DETAIL } from '../mock/mockData';
import styles from './ParticipantPage.module.css';

export default function ParticipantPage() {
  const { sid } = useParams();
  const settlementId = sid || MOCK_SETTLEMENT_DETAIL.id;

  const savedClaim = storage.getParticipantClaim(settlementId, null);
  const [selectedParticipantId, setSelectedParticipantId] = useState(
    savedClaim?.participantId || null
  );
  const [payStatus, setPayStatus] = useState(savedClaim?.payStatus || 'UNPAID');
  const [isBreakdownOpen, setIsBreakdownOpen] = useState(true);
  const [isPaymentSheetOpen, setIsPaymentSheetOpen] = useState(false);

  const selectedParticipant = MOCK_SETTLEMENT_DETAIL.participants.find(
    (p) => p.id === selectedParticipantId
  );

  const handleClaimParticipant = (participant) => {
    if (participant.isHost || participant.claimed) return;
    setSelectedParticipantId(participant.id);
    setPayStatus(participant.payStatus || 'UNPAID');
    storage.setParticipantClaim(settlementId, {
      participantId: participant.id,
      name: participant.name,
      payStatus: participant.payStatus || 'UNPAID',
    });
  };

  const handleResetSelectionForMock = () => {
    storage.clearParticipantClaim(settlementId);
    setSelectedParticipantId(null);
    setPayStatus('UNPAID');
  };

  const handleReportPaid = () => {
    setPayStatus('REPORTED');
    if (selectedParticipant) {
      storage.setParticipantClaim(settlementId, {
        participantId: selectedParticipant.id,
        name: selectedParticipant.name,
        payStatus: 'REPORTED',
      });
    }
  };

  const handleCancelReport = () => {
    setPayStatus('UNPAID');
    if (selectedParticipant) {
      storage.setParticipantClaim(settlementId, {
        participantId: selectedParticipant.id,
        name: selectedParticipant.name,
        payStatus: 'UNPAID',
      });
    }
  };

  // 1단계: 이름 선택 화면 (이름을 고르기 전에는 금액과 계좌번호를 보여주지 않음)
  if (!selectedParticipant) {
    return (
      <AppShell
        title={MOCK_SETTLEMENT_DETAIL.title}
        subtitle="본인 이름을 선택하면 내 정산 금액을 볼 수 있어요"
        backTo="/"
      >
        <section className={styles.claimSection}>
          <div className={styles.claimNotice}>
            <strong>누구신가요?</strong>
            <p>
              본인 이름을 탭해주세요. 이미 다른 기기에서 선택된 이름이나 총무는 선택할 수 없어요.
            </p>
          </div>

          <ul className={styles.nameList}>
            {MOCK_SETTLEMENT_DETAIL.participants.map((p) => {
              const disabled = p.isHost || p.claimed;
              return (
                <li key={p.id}>
                  <button
                    type="button"
                    className={`${styles.nameButton} ${
                      disabled ? styles.nameButtonDisabled : ''
                    }`}
                    disabled={disabled}
                    onClick={() => handleClaimParticipant(p)}
                  >
                    <span className={styles.personName}>{p.name}</span>
                    <span className={styles.stateLabel}>
                      {p.isHost
                        ? '총무 (선택 불가)'
                        : p.claimed
                        ? '이미 선택됨'
                        : '선택하기 →'}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </section>
      </AppShell>
    );
  }

  // 2단계: 내 몫 확인 및 송금 화면
  return (
    <AppShell
      title={`${selectedParticipant.name}님의 정산 내역`}
      subtitle={MOCK_SETTLEMENT_DETAIL.title}
      backTo="/"
      bottomBar={
        <div className={styles.bottomActions}>
          <button
            type="button"
            className={styles.paySheetBtn}
            onClick={() => setIsPaymentSheetOpen(true)}
          >
            💸 송금하기
          </button>
          <button
            type="button"
            className={styles.reportedCta}
            onClick={handleReportPaid}
            disabled={payStatus !== 'UNPAID'}
          >
            {payStatus === 'UNPAID' ? '송금했어요' : '송금 신고 완료'}
          </button>
        </div>
      }
    >
      {/* 내 부담 금액 카드 */}
      <section className={styles.myShareCard}>
        <div className={styles.myShareTop}>
          <span className={styles.myShareLabel}>내가 보낼 금액</span>
          <button
            type="button"
            className={styles.switchNameBtn}
            onClick={handleResetSelectionForMock}
          >
            이름 다시 고르기(Mock)
          </button>
        </div>

        <strong className={styles.myAmount}>
          {selectedParticipant.amount.toLocaleString()}원
        </strong>
        <p className={styles.reasonText}>{selectedParticipant.summaryReason}</p>

        <div className={styles.accountBox}>
          <span>입금 계좌</span>
          <strong>
            {MOCK_SETTLEMENT_DETAIL.bank} {MOCK_SETTLEMENT_DETAIL.accountNumber} (
            {MOCK_SETTLEMENT_DETAIL.accountHolder})
          </strong>
        </div>
      </section>

      {/* 송금 신고 후 안내 문구 */}
      {payStatus === 'REPORTED' && (
        <section className={styles.reportedNotice}>
          <div>
            <strong>🟡 총무에게 송금했다고 알렸어요!</strong>
            <p>총무가 은행 앱 내역과 대조해 입금 확인을 누르면 완료됩니다.</p>
          </div>
          <button
            type="button"
            className={styles.undoBtn}
            onClick={handleCancelReport}
          >
            신고 취소 (되돌리기)
          </button>
        </section>
      )}

      {/* 접고 펼치는 메뉴 내역 */}
      <section className={styles.breakdownSection}>
        <button
          type="button"
          className={styles.accordionToggle}
          onClick={() => setIsBreakdownOpen((prev) => !prev)}
        >
          <span>상세 메뉴 내역 ({selectedParticipant.breakdown.length}건)</span>
          <span>{isBreakdownOpen ? '접기 ▲' : '펼치기 ▼'}</span>
        </button>

        {isBreakdownOpen && (
          <ul className={styles.breakdownList}>
            {selectedParticipant.breakdown.map((row, idx) => (
              <li key={idx} className={styles.breakdownRow}>
                <div>
                  <span className={styles.roundTag}>{row.roundNumber}차 · {row.placeName}</span>
                  <p className={styles.menuTitle}>{row.itemName}</p>
                </div>
                <strong className={styles.sharePrice}>
                  {row.share.toLocaleString()}원
                </strong>
              </li>
            ))}
          </ul>
        )}
      </section>

      <PaymentSheet
        open={isPaymentSheetOpen}
        amount={selectedParticipant.amount}
        bank={MOCK_SETTLEMENT_DETAIL.bank}
        accountNumber={MOCK_SETTLEMENT_DETAIL.accountNumber}
        accountHolder={MOCK_SETTLEMENT_DETAIL.accountHolder}
        onClose={() => setIsPaymentSheetOpen(false)}
        onMarkReported={handleReportPaid}
      />
    </AppShell>
  );
}

