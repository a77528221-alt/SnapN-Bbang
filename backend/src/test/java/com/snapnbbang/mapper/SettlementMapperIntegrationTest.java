package com.snapnbbang.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.snapnbbang.common.IdGenerator;
import com.snapnbbang.common.TokenUtil;
import com.snapnbbang.domain.Settlement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SettlementMapperIntegrationTest {

    @Autowired
    private SettlementMapper settlementMapper;

    @Test
    @DisplayName("settlement 테이블 insert 및 selectById 통합 테스트")
    void insertAndSelectById() {
        String id = IdGenerator.generate();
        String rawAdminToken = TokenUtil.generateToken();
        String adminTokenHash = TokenUtil.sha256Hex(rawAdminToken);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        Settlement settlement = Settlement.builder()
                .id(id)
                .title("11/2 회식")
                .settledOn(LocalDate.of(2026, 11, 2))
                .status("SHARED")
                .adminTokenHash(adminTokenHash)
                .bank("카카오뱅크")
                .accountNumber("3333-00-0000000")
                .accountHolder("김민수")
                .payLink(null)
                .sharedAt(now)
                .accountPurgeAt(now.plusDays(90))
                .expiresAt(now.plusMonths(12))
                .build();

        int inserted = settlementMapper.insert(settlement);
        assertThat(inserted).isEqualTo(1);

        Settlement found = settlementMapper.selectById(id);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getTitle()).isEqualTo("11/2 회식");
        assertThat(found.getSettledOn()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(found.getStatus()).isEqualTo("SHARED");
        assertThat(TokenUtil.verifyToken(rawAdminToken, found.getAdminTokenHash())).isTrue();
        assertThat(found.getBank()).isEqualTo("카카오뱅크");
        assertThat(found.getAccountHolder()).isEqualTo("김민수");
    }
}

