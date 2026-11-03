import { useState } from 'react';
import styles from './PaymentSheet.module.css';

/**
 * 송금하기 바텀 시트 (src/components/PaymentSheet.jsx)
 *
 * TODO [외부 송금 앱 연동 확인 필요]:
 * 1. 토스 딥링크 스킴(supertoss://send?...) 및 파라미터 규격은 공식 문서로 보장되지 않으므로,
 *    실기기 실험 결과에 따라 연결 방식 확정 필요.
 * 2. 카카오페이 송금 링크(https://qr.kakaopay.com/...) 연결 방식 및 금액 자동 입력 지원 여부 확인 필요.
 * 3. 현재 단계에서는 버튼 자리만 구성하고 동작은 TODO 안내로 두었음.
 */
export default function PaymentSheet({
  open,
  amount,
  bank,
  accountNumber,
  accountHolder,
  onClose,
  onMarkReported,
}) {
  const [noticeMessage, setNoticeMessage] = useState('');

  if (!open) return null;

  const handleActionPlaceholder = (label) => {
    // TODO: 실제 토스 딥링크 / 카카오페이 송금 링크 / 클립보드 계좌 복사 동작 구현 예정
    setNoticeMessage(`[TODO] "${label}" 동작 자리가 호출되었습니다. 송금하셨나요?`);
  };

  return (
    <div className={styles.backdrop} onClick={onClose} role="presentation">
      <section
        className={styles.sheet}
        onClick={(e) => e.stopPropagation()}
        aria-label="송금하기 시트"
      >
        <div className={styles.handle} />

        <header className={styles.sheetHeader}>
          <span className={styles.sheetLabel}>보내실 금액</span>
          <strong className={styles.amountText}>
            {Number(amount || 0).toLocaleString()}원
          </strong>
          <p className={styles.accountInfo}>
            {bank} {accountNumber} ({accountHolder})
          </p>
        </header>

        <div className={styles.buttonGroup}>
          <button
            type="button"
            className={styles.CopyButton}
            onClick={() => handleActionPlaceholder('계좌 복사')}
          >
            📋 계좌번호 복사 (기본)
          </button>
          <div className={styles.appButtonRow}>
            <button
              type="button"
              className={styles.appButton}
              onClick={() => handleActionPlaceholder('토스로 송금')}
            >
              토스 열기 (TODO)
            </button>
            <button
              type="button"
              className={styles.appButton}
              onClick={() => handleActionPlaceholder('카카오페이로 송금')}
            >
              카카오페이 열기 (TODO)
            </button>
          </div>
        </div>

        <p className={styles.fallbackNotice}>
          열리지 않으면 계좌 복사를 써 주세요.
        </p>

        {noticeMessage && (
          <div className={styles.returnPrompt}>
            <p>{noticeMessage}</p>
            <button
              type="button"
              className={styles.reportedBtn}
              onClick={() => {
                onMarkReported();
                onClose();
              }}
            >
              네, 송금했어요!
            </button>
          </div>
        )}

        <button type="button" className={styles.closeBtn} onClick={onClose}>
          닫기
        </button>
      </section>
    </div>
  );
}

