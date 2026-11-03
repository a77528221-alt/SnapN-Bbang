import { Link, useNavigate } from 'react-router-dom';
import styles from './AppShell.module.css';

export default function AppShell({
  title = '스냅 N빵',
  subtitle,
  backTo,
  showProfileLink = false,
  bottomBar,
  children,
}) {
  const navigate = useNavigate();

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          {backTo && (
            <button
              type="button"
              className={styles.backButton}
              onClick={() => navigate(backTo)}
              aria-label="뒤로 가기"
            >
              ←
            </button>
          )}
          <div>
            <h1 className={styles.title}>{title}</h1>
            {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
          </div>
        </div>
        {showProfileLink && (
          <Link to="/profile" className={styles.profileButton} aria-label="프로필 설정">
            <span className={styles.profileIcon} aria-hidden="true">👤</span>
            <span>프로필</span>
          </Link>
        )}
      </header>

      <main className={styles.main}>{children}</main>

      {bottomBar && <footer className={styles.bottomBar}>{bottomBar}</footer>}
    </div>
  );
}

