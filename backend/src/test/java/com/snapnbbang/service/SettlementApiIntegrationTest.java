package com.snapnbbang.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapnbbang.common.ConflictException;
import com.snapnbbang.common.NotFoundException;
import com.snapnbbang.common.TokenUtil;
import com.snapnbbang.domain.Settlement;
import com.snapnbbang.domain.SettlementParticipant;
import com.snapnbbang.dto.ParticipantClaimRequest;
import com.snapnbbang.dto.ParticipantClaimResponse;
import com.snapnbbang.dto.SettlementCreateRequest;
import com.snapnbbang.dto.SettlementCreateRequest.ItemRequest;
import com.snapnbbang.dto.SettlementCreateRequest.ParticipantRequest;
import com.snapnbbang.dto.SettlementCreateRequest.RoundRequest;
import com.snapnbbang.dto.SettlementCreateResponse;
import com.snapnbbang.dto.SettlementPublicResponse;
import com.snapnbbang.dto.SettlementPublicResponse.ParticipantSummary;
import com.snapnbbang.mapper.SettlementMapper;
import com.snapnbbang.mapper.SettlementParticipantMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class SettlementApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private SettlementMapper settlementMapper;

    @Autowired
    private SettlementParticipantMapper settlementParticipantMapper;

    @Test
    @DisplayName("정산 생성 후 공개 열람 시 금액·계좌·메뉴는 절대 포함되지 않는다")
    void publicViewDoesNotContainAmountsAccountOrMenus() throws Exception {
        SettlementCreateRequest request = sampleCreateRequest();

        MvcResult createResult = mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settlementId").isString())
                .andExpect(jsonPath("$.adminToken").isString())
                .andReturn();

        SettlementCreateResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                SettlementCreateResponse.class);

        // DB에는 총무 토큰 해시만 저장되고 원문과 다르며, 상수 시간 검증 통과
        Settlement savedSettlement = settlementMapper.selectById(created.getSettlementId());
        assertThat(savedSettlement).isNotNull();
        assertThat(savedSettlement.getAdminTokenHash()).isNotEqualTo(created.getAdminToken());
        assertThat(TokenUtil.verifyToken(created.getAdminToken(), savedSettlement.getAdminTokenHash())).isTrue();

        // 공개 열람 응답 검증
        MvcResult publicResult = mockMvc.perform(get("/api/settlements/{sid}", created.getSettlementId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("11/2 금요일 회식"))
                .andExpect(jsonPath("$.settledOn").value("2026-11-02"))
                .andExpect(jsonPath("$.participants.length()").value(4))
                .andReturn();

        String rawBody = publicResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(rawBody);

        // 최상위 필드는 title, settledOn, participants 3개만 존재해야 함
        assertThat(root.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("title", "settledOn", "participants");

        // 각 참여자 객체에도 id, name, isHost, claimed 외에 금액·상태 등이 없어야 함
        for (JsonNode participantNode : root.get("participants")) {
            assertThat(participantNode.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder("id", "name", "isHost", "claimed");
        }

        // 문자열 전체에도 계좌번호, 은행명, 송금링크, 메뉴명, 금액이 노출되지 않아야 함
        assertThat(rawBody)
                .doesNotContain("카카오뱅크")
                .doesNotContain("3333-01-2345678")
                .doesNotContain("김민수")
                .doesNotContain("qr.kakaopay.com")
                .doesNotContain("삼겹살")
                .doesNotContain("소주")
                .doesNotContain("40000")
                .doesNotContain("22333");
    }

    @Test
    @DisplayName("총무 참여자는 점유(claim)할 수 없으며 400을 반환한다")
    void hostCannotBeClaimed() throws Exception {
        SettlementCreateResponse created = settlementService.createSettlement(sampleCreateRequest());
        SettlementPublicResponse publicView = settlementService.getSettlementPublic(created.getSettlementId());

        ParticipantSummary hostSummary = publicView.getParticipants().stream()
                .filter(ParticipantSummary::isHost)
                .findFirst()
                .orElseThrow();

        ParticipantClaimRequest claimRequest = ParticipantClaimRequest.builder()
                .participantId(hostSummary.getId())
                .build();

        mockMvc.perform(post("/api/settlements/{sid}/claim", created.getSettlementId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(claimRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값을 확인해 주세요"));
    }

    @Test
    @DisplayName("존재하지 않는 정산 조회·점유 및 잘못된 토큰 검증은 모두 동일한 404를 반환한다")
    void nonExistentSettlementAndInvalidAuthReturn404() throws Exception {
        String missingSid = "NonExistentSettleId12";

        mockMvc.perform(get("/api/settlements/{sid}", missingSid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("찾을 수 없어요"));

        mockMvc.perform(post("/api/settlements/{sid}/claim", missingSid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("찾을 수 없어요"));

        SettlementCreateResponse created = settlementService.createSettlement(sampleCreateRequest());
        String sid = created.getSettlementId();

        // 올바른 총무 토큰 검증 통과
        Settlement verified = settlementService.verifyAdminToken(sid, "Bearer " + created.getAdminToken());
        assertThat(verified.getId()).isEqualTo(sid);

        // 틀린 총무 토큰 또는 없는 정산 모두 동일한 NotFoundException("찾을 수 없어요")
        assertThatThrownBy(() -> settlementService.verifyAdminToken(sid, "Bearer wrong-token"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("찾을 수 없어요");
        assertThatThrownBy(() -> settlementService.verifyAdminToken(missingSid, "Bearer " + created.getAdminToken()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("찾을 수 없어요");
    }

    @Test
    @DisplayName("먹은 사람이 한 명도 없는 메뉴가 있으면 400을 반환한다")
    void menuWithNoEatersReturns400() throws Exception {
        SettlementCreateRequest request = sampleCreateRequest();
        // 1차 소주 메뉴에서 참석자 전원을 제외
        request.getRounds().get(0).getItems().get(1)
                .setExcludedNames(List.of("민수", "철수", "영희", "지민"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값을 확인해 주세요"));
    }

    @Test
    @DisplayName("허용되지 않은 payLink 도메인(qr.kakaopay.com 외)은 400을 반환한다")
    void invalidPayLinkDomainReturns400() throws Exception {
        SettlementCreateRequest request = sampleCreateRequest();
        request.setPayLink("https://evil.example.com/pay");

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값을 확인해 주세요"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시에 여러 스레드가 같은 참여자 이름을 점유해도 정확히 1명만 성공하고 나머지는 409를 받는다")
    void concurrentClaimAllowsOnlyOneWinner() throws Exception {
        String settlementId = null;
        try {
            SettlementCreateResponse created = settlementService.createSettlement(sampleCreateRequest());
            settlementId = created.getSettlementId();

            SettlementPublicResponse publicView = settlementService.getSettlementPublic(settlementId);
            ParticipantSummary targetParticipant = publicView.getParticipants().stream()
                    .filter(p -> !p.isHost() && p.getName().equals("철수"))
                    .findFirst()
                    .orElseThrow();

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            List<ParticipantClaimResponse> winners = Collections.synchronizedList(new ArrayList<>());
            List<Future<?>> futures = new ArrayList<>();

            String targetSid = settlementId;
            Long targetPid = targetParticipant.getId();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        ParticipantClaimResponse response = settlementService.claimParticipant(
                                targetSid,
                                new ParticipantClaimRequest(targetPid));
                        successCount.incrementAndGet();
                        winners.add(response);
                    } catch (ConflictException ex) {
                        conflictCount.incrementAndGet();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            executor.shutdownNow();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
            assertThat(winners).hasSize(1);

            String winningToken = winners.get(0).getParticipantToken();
            assertThat(winningToken).isNotBlank();

            // 발급된 참여자 토큰으로 검증 성공 및 DB에는 해시만 저장되었는지 확인
            SettlementParticipant verifiedParticipant = settlementService.verifyParticipantToken(
                    settlementId, "Bearer " + winningToken);
            assertThat(verifiedParticipant.getId()).isEqualTo(targetPid);

            SettlementParticipant dbParticipant = settlementParticipantMapper.selectByIdAndSettlementId(
                    targetPid, settlementId);
            assertThat(dbParticipant.getTokenHash()).isNotEqualTo(winningToken);
            assertThat(TokenUtil.verifyToken(winningToken, dbParticipant.getTokenHash())).isTrue();
        } finally {
            if (settlementId != null) {
                settlementMapper.deleteById(settlementId);
            }
        }
    }

    private SettlementCreateRequest sampleCreateRequest() {
        return SettlementCreateRequest.builder()
                .title("11/2 금요일 회식")
                .settledOn(LocalDate.of(2026, 11, 2))
                .bank("카카오뱅크")
                .accountNumber("3333-01-2345678")
                .accountHolder("김민수")
                .payLink("https://qr.kakaopay.com/281006011000000000000000")
                .participants(List.of(
                        new ParticipantRequest("민수", true),
                        new ParticipantRequest("철수", false),
                        new ParticipantRequest("영희", false),
                        new ParticipantRequest("지민", false)
                ))
                .rounds(List.of(
                        RoundRequest.builder()
                                .roundNumber(1)
                                .placeName("통돼지집")
                                .receiptTotal(50000)
                                .adjustmentAmount(0)
                                .absentNames(List.of())
                                .items(List.of(
                                        ItemRequest.builder()
                                                .name("삼겹살")
                                                .qty(4)
                                                .price(40000)
                                                .excludedNames(List.of())
                                                .build(),
                                        ItemRequest.builder()
                                                .name("소주")
                                                .qty(2)
                                                .price(10000)
                                                .excludedNames(List.of("영희"))
                                                .build()
                                ))
                                .build(),
                        RoundRequest.builder()
                                .roundNumber(2)
                                .placeName("역전할머니맥주")
                                .receiptTotal(27000)
                                .adjustmentAmount(0)
                                .absentNames(List.of("지민"))
                                .items(List.of(
                                        ItemRequest.builder()
                                                .name("먹태")
                                                .qty(1)
                                                .price(15000)
                                                .excludedNames(List.of())
                                                .build(),
                                        ItemRequest.builder()
                                                .name("맥주")
                                                .qty(3)
                                                .price(12000)
                                                .excludedNames(List.of())
                                                .build()
                                ))
                                .build()
                ))
                .build();
    }
}
