import { useState } from 'react';
import AppShell from '../components/AppShell';
import { storage } from '../lib/storage';
import { BANK_OPTIONS, MOCK_DEFAULT_PROFILE } from '../mock/mockData';
import styles from './ProfilePage.module.css';

const EMPTY_PROFILE = {
  name: '',
  bank: BANK_OPTIONS[0],
  accountNumber: '',
  accountHolder: '',
  payLink: '',
};

export default function ProfilePage() {
  const [form, setForm] = useState(() =>
    storage.getProfile(MOCK_DEFAULT_PROFILE)
  );
  const [savedNotice, setSavedNotice] = useState('');

  const handleChange = (field) => (event) => {
    setForm((prev) => ({
      ...prev,
      [field]: event.target.value,
    }));
    setSavedNotice('');
  };

  const handleSave = (event) => {
    event.preventDefault();
    storage.setProfile(form);
    setSavedNotice('프로필 정보를 이 기기에 저장했어요.');
  };

  const handleClearStoredInfo = () => {
    const ok = window.confirm('이 기기에 저장된 프로필 정보를 모두 삭제할까요?');
    if (!ok) return;
    storage.clearProfile();
    setForm(EMPTY_PROFILE);
    setSavedNotice('저장된 정보를 삭제했어요.');
  };

  return (
    <AppShell
      title="총무 프로필 설정"
      subtitle="이름·계좌는 서버가 아닌 이 기기에만 저장돼요"
      backTo="/"
      bottomBar={
        <button
          type="submit"
          form="profile-form"
          className={styles.saveCta}
        >
          프로필 저장하기
        </button>
      }
    >
      <form id="profile-form" className={styles.form} onSubmit={handleSave}>
        <label className={styles.field}>
          <span className={styles.label}>총무 이름</span>
          <input
            type="text"
            className={styles.input}
            value={form.name}
            onChange={handleChange('name')}
            placeholder="예: 민수"
            maxLength={30}
            required
          />
        </label>

        <label className={styles.field}>
          <span className={styles.label}>은행</span>
          <select
            className={styles.select}
            value={form.bank}
            onChange={handleChange('bank')}
          >
            {BANK_OPTIONS.map((bankName) => (
              <option key={bankName} value={bankName}>
                {bankName}
              </option>
            ))}
          </select>
        </label>

        <label className={styles.field}>
          <span className={styles.label}>계좌번호</span>
          <input
            type="text"
            className={styles.input}
            value={form.accountNumber}
            onChange={handleChange('accountNumber')}
            placeholder="예: 3333-01-2345678"
            maxLength={40}
          />
        </label>

        <label className={styles.field}>
          <span className={styles.label}>예금주</span>
          <input
            type="text"
            className={styles.input}
            value={form.accountHolder}
            onChange={handleChange('accountHolder')}
            placeholder="예: 김민수"
            maxLength={30}
          />
        </label>

        <label className={styles.field}>
          <span className={styles.label}>카카오페이 송금 링크 (선택)</span>
          <input
            type="url"
            className={styles.input}
            value={form.payLink}
            onChange={handleChange('payLink')}
            placeholder="https://qr.kakaopay.com/..."
            maxLength={255}
          />
        </label>

        {savedNotice && <p className={styles.notice}>{savedNotice}</p>}
      </form>

      <section className={styles.clearBox}>
        <p className={styles.clearDesc}>
          공용 기기이거나 저장된 계좌 정보를 지우고 싶다면 아래 버튼을 눌러 삭제하세요.
        </p>
        <button
          type="button"
          className={styles.clearBtn}
          onClick={handleClearStoredInfo}
        >
          저장된 정보 삭제
        </button>
      </section>
    </AppShell>
  );
}

