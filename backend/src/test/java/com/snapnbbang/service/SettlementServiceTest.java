package com.snapnbbang.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.snapnbbang.common.ConflictException;
import com.snapnbbang.common.GlobalExceptionHandler;
import com.snapnbbang.common.NotFoundException;
import com.snapnbbang.common.TokenUtil;
import com.snapnbbang.controller.SettlementController;
import com.snapnbbang.domain.Item;
import com.snapnbbang.domain.ItemExclusion;
import com.snapnbbang.domain.RoundAbsence;
import com.snapnbbang.domain.SettleRound;
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
import com.snapnbbang.mapper.ItemExclusionMapper;
import com.snapnbbang.mapper.ItemMapper;
import com.snapnbbang.mapper.RoundAbsenceMapper;
import com.snapnbbang.mapper.SettleRoundMapper;
import com.snapnbbang.mapper.SettlementMapper;
import com.snapnbbang.mapper.SettlementParticipantMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SettlementServiceTest {

    private SettlementService settlementService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final Map<String, Settlement> settlementStore = new ConcurrentHashMap<>();
    private final Map<Long, SettlementParticipant> participantStore = new ConcurrentHashMap<>();
    private final List<SettleRound> roundStore = Collections.synchronizedList(new ArrayList<>());
    private final List<RoundAbsence> absenceStore = Collections.synchronizedList(new ArrayList<>());
    private final List<Item> itemStore = Collections.synchronizedList(new ArrayList<>());
    private final List<ItemExclusion> exclusionStore = Collections.synchronizedList(new ArrayList<>());

    private final AtomicLong participantIdSeq = new AtomicLong(1L);
    private final AtomicLong roundIdSeq = new AtomicLong(1L);
    private final AtomicLong itemIdSeq = new AtomicLong(1L);

    @BeforeEach
    void setUp() {
        settlementStore.clear();
        participantStore.clear();
        roundStore.clear();
        absenceStore.clear();
        itemStore.clear();
        exclusionStore.clear();

        SettlementMapper settlementMapper = new SettlementMapper() {
            @Override
            public int insert(Settlement settlement) {
                settlementStore.put(settlement.getId(), settlement);
                return 1;
            }

            @Override
            public Settlement selectById(String id) {
                return settlementStore.get(id);
            }

            @Override
            public int deleteById(String id) {
                return settlementStore.remove(id) != null ? 1 : 0;
            }
        };

        SettlementParticipantMapper settlementParticipantMapper = new SettlementParticipantMapper() {
            @Override
            public int insert(SettlementParticipant participant) {
                long id = participantIdSeq.getAndIncrement();
                participant.setId(id);
                participantStore.put(id, participant);
                return 1;
            }

            @Override
            public List<SettlementParticipant> selectBySettlementId(String settlementId) {
                return participantStore.values().stream()
                        .filter(p -> settlementId.equals(p.getSettlementId()))
                        .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                        .toList();
            }

            @Override
            public SettlementParticipant selectByIdAndSettlementId(Long id, String settlementId) {
                SettlementParticipant p = participantStore.get(id);
                if (p != null && settlementId.equals(p.getSettlementId())) {
                    return p;
                }
                return null;
            }

            @Override
            public int claimParticipant(Long id, String settlementId, String tokenHash) {
                SettlementParticipant p = participantStore.get(id);
                if (p == null || !settlementId.equals(p.getSettlementId())) {
                    return 0;
                }
                synchronized (p) {
                    if (!p.isHost() && p.getTokenHash() == null) {
                        p.setTokenHash(tokenHash);
                        p.setClaimedAt(LocalDateTime.now());
                        return 1;
                    }
                    return 0;
                }
            }

            @Override
            public int releaseClaim(Long id, String settlementId) {
                SettlementParticipant p = participantStore.get(id);
                if (p == null || !settlementId.equals(p.getSettlementId())) {
                    return 0;
                }
                synchronized (p) {
                    if (!p.isHost()) {
                        p.setTokenHash(null);
                        p.setClaimedAt(null);
                        p.setPayStatus("UNPAID");
                        p.setReportedAt(null);
                        p.setConfirmedAt(null);
                        return 1;
                    }
                    return 0;
                }
            }

            @Override
            public int reportPayment(Long id, String settlementId, String tokenHash) {
                SettlementParticipant p = participantStore.get(id);
                if (p == null || !settlementId.equals(p.getSettlementId())) {
                    return 0;
                }
                synchronized (p) {
                    if (!p.isHost()
                            && tokenHash != null
                            && tokenHash.equals(p.getTokenHash())
                            && "UNPAID".equals(p.getPayStatus())) {
                        p.setPayStatus("REPORTED");
                        p.setReportedAt(LocalDateTime.now());
                        return 1;
                    }
                    return 0;
                }
            }

            @Override
            public int cancelReportPayment(Long id, String settlementId, String tokenHash) {
                SettlementParticipant p = participantStore.get(id);
                if (p == null || !settlementId.equals(p.getSettlementId())) {
                    return 0;
                }
                synchronized (p) {
                    if (!p.isHost()
                            && tokenHash != null
                            && tokenHash.equals(p.getTokenHash())
                            && "REPORTED".equals(p.getPayStatus())) {
                        p.setPayStatus("UNPAID");
                        p.setReportedAt(null);
                        return 1;
                    }
                    return 0;
                }
            }

            @Override
            public int confirmPayment(Long id, String settlementId) {
                SettlementParticipant p = participantStore.get(id);
                if (p == null || !settlementId.equals(p.getSettlementId())) {
                    return 0;
                }
                synchronized (p) {
                    if ("REPORTED".equals(p.getPayStatus())) {
                        p.setPayStatus("CONFIRMED");
                        p.setConfirmedAt(LocalDateTime.now());
                        return 1;
                    }
                    return 0;
                }
            }
        };

        SettleRoundMapper settleRoundMapper = new SettleRoundMapper() {
            @Override
            public int insert(SettleRound round) {
                round.setId(roundIdSeq.getAndIncrement());
                roundStore.add(round);
                return 1;
            }

            @Override
            public List<SettleRound> selectBySettlementId(String settlementId) {
                return roundStore.stream()
                        .filter(r -> settlementId.equals(r.getSettlementId()))
                        .toList();
            }

            @Override
            public SettleRound selectByIdAndSettlementId(Long id, String settlementId) {
                return roundStore.stream()
                        .filter(r -> id.equals(r.getId()) && settlementId.equals(r.getSettlementId()))
                        .findFirst()
                        .orElse(null);
            }
        };

        RoundAbsenceMapper roundAbsenceMapper = new RoundAbsenceMapper() {
            @Override
            public int insert(RoundAbsence roundAbsence) {
                absenceStore.add(roundAbsence);
                return 1;
            }

            @Override
            public List<RoundAbsence> selectBySettlementId(String settlementId) {
                return absenceStore.stream()
                        .filter(a -> settlementId.equals(a.getSettlementId()))
                        .toList();
            }
        };

        ItemMapper itemMapper = new ItemMapper() {
            @Override
            public int insert(Item item) {
                item.setId(itemIdSeq.getAndIncrement());
                itemStore.add(item);
                return 1;
            }

            @Override
            public List<Item> selectBySettlementId(String settlementId) {
                return itemStore.stream()
                        .filter(i -> settlementId.equals(i.getSettlementId()))
                        .toList();
            }
        };

        ItemExclusionMapper itemExclusionMapper = new ItemExclusionMapper() {
            @Override
            public int insert(ItemExclusion itemExclusion) {
                exclusionStore.add(itemExclusion);
                return 1;
            }

            @Override
            public List<ItemExclusion> selectBySettlementId(String settlementId) {
                return exclusionStore.stream()
                        .filter(e -> settlementId.equals(e.getSettlementId()))
                        .toList();
            }
        };

        settlementService = new SettlementService(
                settlementMapper,
                settlementParticipantMapper,
                settleRoundMapper,
                roundAbsenceMapper,
                itemMapper,
                itemExclusionMapper,
                new SettlementCalculator()
        );

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SettlementController controller = new SettlementController(settlementService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("1. 동시 점유 시 한 명만 성공하고 나머지는 409(ConflictException)를 받는다")
    void concurrentClaimAllowsOnlyOneWinner() throws Exception {
        SettlementCreateResponse created = settlementService.createSettlement(sampleCreateRequest());
        String sid = created.getSettlementId();

        SettlementPublicResponse publicView = settlementService.getSettlementPublic(sid);
        ParticipantSummary target = publicView.getParticipants().stream()
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

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    ParticipantClaimResponse res = settlementService.claimParticipant(
                            sid, new ParticipantClaimRequest(target.getId()));
                    successCount.incrementAndGet();
                    winners.add(res);
                } catch (ConflictException ex) {
                    conflictCount.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(winners).hasSize(1);

        // 이미 점유된 이름을 HTTP로 다시 요청하면 409 응답
        mockMvc.perform(post("/api/settlements/{sid}/claim", sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ParticipantClaimRequest(target.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 선택된 이름이에요"));
    }

    @Test
    @DisplayName("2. 총무는 점유(claim)할 수 없으며 400을 반환한다")
    void hostCannotBeClaimed() throws Exception {
        SettlementCreateResponse created = settlementService.createSettlement(sampleCreateRequest());
        SettlementPublicResponse publicView = settlementService.getSettlementPublic(created.getSettlementId());

        ParticipantSummary host = publicView.getParticipants().stream()
                .filter(ParticipantSummary::isHost)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/settlements/{sid}/claim", created.getSettlementId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ParticipantClaimRequest(host.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값을 확인해 주세요"));
    }

    @Test
    @DisplayName("3. 없는 정산 조회·점유 및 인증 실패는 모두 동일한 404를 반환한다")
    void nonExistentSettlementAndAuthFailureReturn404() throws Exception {
        String missingSid = "NonExistentSettleId12";

        mockMvc.perform(get("/api/settlements/{sid}", missingSid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("찾을 수 없어요"));

        mockMvc.perform(post("/api/settlements/{sid}/claim", missingSid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("찾을 수 없어요"));

        SettlementCreateResponse created = settlementService.createSettlement(sampleCreateRequest());
        String sid = created.getSettlementId();

        assertThat(settlementService.verifyAdminToken(sid, "Bearer " + created.getAdminToken()).getId())
                .isEqualTo(sid);

        assertThatThrownBy(() -> settlementService.verifyAdminToken(sid, "Bearer invalid-token"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("찾을 수 없어요");
        assertThatThrownBy(() -> settlementService.verifyAdminToken(missingSid, "Bearer " + created.getAdminToken()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("찾을 수 없어요");
    }

    @Test
    @DisplayName("4. 먹은 사람이 없는 메뉴 또는 잘못된 payLink 도메인은 400을 반환한다")
    void zeroEaterMenuAndInvalidPayLinkReturn400() throws Exception {
        SettlementCreateRequest zeroEaterReq = sampleCreateRequest();
        zeroEaterReq.getRounds().get(0).getItems().get(1)
                .setExcludedNames(List.of("민수", "철수", "영희", "지민"));

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroEaterReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값을 확인해 주세요"));

        SettlementCreateRequest badPayLinkReq = sampleCreateRequest();
        badPayLinkReq.setPayLink("https://malicious.example.com/pay");

        mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPayLinkReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 값을 확인해 주세요"));
    }

    @Test
    @DisplayName("5. 공개 열람(GET /api/settlements/{sid})에는 금액·계좌·메뉴가 절대 포함되지 않는다")
    void publicViewContainsNoAmountsAccountOrMenus() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCreateRequest())))
                .andExpect(status().isCreated())
                .andReturn();

        SettlementCreateResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                SettlementCreateResponse.class);

        Settlement saved = settlementStore.get(created.getSettlementId());
        assertThat(saved.getStatus()).isEqualTo("SHARED");
        assertThat(saved.getAdminTokenHash()).isNotEqualTo(created.getAdminToken());
        assertThat(TokenUtil.verifyToken(created.getAdminToken(), saved.getAdminTokenHash())).isTrue();

        MvcResult publicResult = mockMvc.perform(get("/api/settlements/{sid}", created.getSettlementId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("11/2 금요일 회식"))
                .andExpect(jsonPath("$.settledOn").value("2026-11-02"))
                .andExpect(jsonPath("$.participants.length()").value(4))
                .andReturn();

        String rawJson = publicResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(rawJson);

        assertThat(root.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("title", "settledOn", "participants");
        for (JsonNode pNode : root.get("participants")) {
            assertThat(pNode.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder("id", "name", "isHost", "claimed");
        }
        assertThat(rawJson)
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
    @DisplayName("6. 모든 MyBatis Mapper XML은 ${}를 쓰지 않고 정산 ID 조건을 포함한다")
    void mapperXmlsUseOnlyPreparedBindingsAndIncludeSettlementId() throws Exception {
        List<String> xmlFiles = List.of(
                "SettlementMapper.xml",
                "SettlementParticipantMapper.xml",
                "SettleRoundMapper.xml",
                "RoundAbsenceMapper.xml",
                "ItemMapper.xml",
                "ItemExclusionMapper.xml"
        );
        for (String fileName : xmlFiles) {
            Path xmlPath = Path.of("src/main/resources/mapper", fileName);
            if (!Files.exists(xmlPath)) {
                xmlPath = Path.of("backend/src/main/resources/mapper", fileName);
            }
            String content = Files.readString(xmlPath, StandardCharsets.UTF_8);
            assertThat(content)
                    .as(fileName + " must not contain ${}")
                    .doesNotContain("${");
            assertThat(content)
                    .as(fileName + " must include settlement id condition")
                    .containsAnyOf("#{id}", "#{settlementId}");
        }
    }

    @Test
    @DisplayName("7. 토큰 없이 /me 접근 불가(404), 남의(다른 정산) 토큰으로 접근 불가(404)")
    void getMyShareWithoutTokenOrWithOtherTokenReturns404() throws Exception {
        SettlementCreateResponse created1 = settlementService.createSettlement(sampleCreateRequest());
        String sid1 = created1.getSettlementId();

        SettlementCreateResponse created2 = settlementService.createSettlement(sampleCreateRequest());
        String sid2 = created2.getSettlementId();
        ParticipantSummary otherParticipant = settlementService.getSettlementPublic(sid2).getParticipants().stream()
                .filter(p -> !p.isHost() && p.getName().equals("철수"))
                .findFirst()
                .orElseThrow();
        ParticipantClaimResponse otherClaim = settlementService.claimParticipant(
                sid2, new ParticipantClaimRequest(otherParticipant.getId()));

        // 토큰 없이 /me 접근 -> 404
        mockMvc.perform(get("/api/settlements/{sid}/me", sid1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("찾을 수 없어요"));

        // 다른 정산의 참여자 토큰으로 sid1의 /me 접근 -> 404
        mockMvc.perform(get("/api/settlements/{sid}/me", sid1)
                        .header("Authorization", "Bearer " + otherClaim.getParticipantToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("찾을 수 없어요"));

        // 총무 토큰으로 /me 접근 -> 참여자 토큰이 아니므로 404
        mockMvc.perform(get("/api/settlements/{sid}/me", sid1)
                        .header("Authorization", "Bearer " + created1.getAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("찾을 수 없어요"));
    }

    @Test
    @DisplayName("8. /me 응답에는 본인 내역만 포함되고 다른 참여자의 이름·금액이 없으며, 계좌가 NULL이면 account는 null이다")
    void getMyShareContainsOnlyMyDataAndHandlesNullAccount() throws Exception {
        SettlementCreateResponse created = settlementService.createSettlement(sampleCreateRequest());
        String sid = created.getSettlementId();

        SettlementPublicResponse publicView = settlementService.getSettlementPublic(sid);
        ParticipantSummary younghee = publicView.getParticipants().stream()
                .filter(p -> p.getName().equals("영희"))
                .findFirst()
                .orElseThrow();
        ParticipantSummary jimin = publicView.getParticipants().stream()
                .filter(p -> p.getName().equals("지민"))
                .findFirst()
                .orElseThrow();

        ParticipantClaimResponse youngheeClaim = settlementService.claimParticipant(
                sid, new ParticipantClaimRequest(younghee.getId()));
        ParticipantClaimResponse jiminClaim = settlementService.claimParticipant(
                sid, new ParticipantClaimRequest(jimin.getId()));

        // 영희 조회: 1차 소주 제외(excludedItems: ["소주"]), 2차 참석, 총 부담액 19000원
        MvcResult youngheeResult = mockMvc.perform(get("/api/settlements/{sid}/me", sid)
                        .header("Authorization", "Bearer " + youngheeClaim.getParticipantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("영희"))
                .andExpect(jsonPath("$.amount").value(19000))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.excludedItems.length()").value(1))
                .andExpect(jsonPath("$.excludedItems[0]").value("소주"))
                .andExpect(jsonPath("$.absentRounds.length()").value(0))
                .andExpect(jsonPath("$.account.bank").value("카카오뱅크"))
                .andExpect(jsonPath("$.account.accountNumber").value("3333-01-2345678"))
                .andExpect(jsonPath("$.account.accountHolder").value("김민수"))
                .andExpect(jsonPath("$.payStatus").value("UNPAID"))
                .andReturn();

        String rawYoungheeJson = youngheeResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 다른 참여자(철수 22333, 지민 13333, 민수 22334)의 금액이나 이름이 노출되지 않아야 함
        assertThat(rawYoungheeJson)
                .doesNotContain("22333")
                .doesNotContain("13333")
                .doesNotContain("22334")
                .doesNotContain("철수")
                .doesNotContain("지민");

        // 계좌 정보가 삭제(NULL)된 경우 account가 null로 내려오는지 확인
        Settlement savedSettlement = settlementStore.get(sid);
        savedSettlement.setBank(null);
        savedSettlement.setAccountNumber(null);
        savedSettlement.setAccountHolder(null);

        // 지민 조회: 2차 불참(absentRounds: [2]), 총 부담액 13333원, account는 null
        mockMvc.perform(get("/api/settlements/{sid}/me", sid)
                        .header("Authorization", "Bearer " + jiminClaim.getParticipantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("지민"))
                .andExpect(jsonPath("$.amount").value(13333))
                .andExpect(jsonPath("$.absentRounds.length()").value(1))
                .andExpect(jsonPath("$.absentRounds[0]").value(2))
                .andExpect(jsonPath("$.account").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("9. 송금 신고(UNPAID->REPORTED), 중복 신고 409, 신고 취소(REPORTED->UNPAID), 입금 확인(CONFIRMED) 후 취소 409")
    void reportAndCancelReportLifecycle() throws Exception {
        SettlementCreateResponse created = settlementService.createSettlement(sampleCreateRequest());
        String sid = created.getSettlementId();

        ParticipantSummary chulsoo = settlementService.getSettlementPublic(sid).getParticipants().stream()
                .filter(p -> p.getName().equals("철수"))
                .findFirst()
                .orElseThrow();
        ParticipantClaimResponse claim = settlementService.claimParticipant(
                sid, new ParticipantClaimRequest(chulsoo.getId()));
        String authHeader = "Bearer " + claim.getParticipantToken();

        // 1) 첫 송금 신고 성공 (UNPAID -> REPORTED)
        mockMvc.perform(post("/api/settlements/{sid}/report", sid)
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payStatus").value("REPORTED"));

        assertThat(participantStore.get(chulsoo.getId()).getPayStatus()).isEqualTo("REPORTED");
        assertThat(participantStore.get(chulsoo.getId()).getReportedAt()).isNotNull();

        // 2) 이미 REPORTED 상태에서 다시 신고하면 409
        mockMvc.perform(post("/api/settlements/{sid}/report", sid)
                        .header("Authorization", authHeader))
                .andExpect(status().isConflict());

        // 3) 신고 취소 성공 (REPORTED -> UNPAID, reported_at NULL)
        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/settlements/{sid}/report", sid)
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payStatus").value("UNPAID"));

        assertThat(participantStore.get(chulsoo.getId()).getPayStatus()).isEqualTo("UNPAID");
        assertThat(participantStore.get(chulsoo.getId()).getReportedAt()).isNull();

        // 4) 다시 신고 후 총무 입금 확인(CONFIRMED)된 뒤에는 취소 시 409, 재신고 시 409
        mockMvc.perform(post("/api/settlements/{sid}/report", sid)
                        .header("Authorization", authHeader))
                .andExpect(status().isOk());

        participantStore.get(chulsoo.getId()).setPayStatus("CONFIRMED");
        participantStore.get(chulsoo.getId()).setConfirmedAt(LocalDateTime.now());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/settlements/{sid}/report", sid)
                        .header("Authorization", authHeader))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/settlements/{sid}/report", sid)
                        .header("Authorization", authHeader))
                .andExpect(status().isConflict());
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
