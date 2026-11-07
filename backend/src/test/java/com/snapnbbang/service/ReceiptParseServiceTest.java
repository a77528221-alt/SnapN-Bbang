package com.snapnbbang.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapnbbang.common.GlobalExceptionHandler;
import com.snapnbbang.common.RateLimitExceededException;
import com.snapnbbang.common.ReceiptParseException;
import com.snapnbbang.controller.ReceiptController;
import com.snapnbbang.dto.ReceiptParseResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReceiptParseServiceTest {

    private static final byte[] VALID_JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10
    };

    private static final byte[] VALID_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
    };

    private static final byte[] VALID_WEBP_BYTES = new byte[]{
            0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x20
    };

    private ObjectMapper objectMapper;
    private GeminiClient mockGeminiClient;
    private ReceiptParseService receiptParseService;
    private ReceiptRateLimiter rateLimiter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockGeminiClient = new GeminiClient(true, "", "");
        receiptParseService = new ReceiptParseService(mockGeminiClient, objectMapper);
        rateLimiter = new ReceiptRateLimiter();
        ReceiptController controller = new ReceiptController(receiptParseService, rateLimiter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GEMINI_MOCK=true 모드에서 유효한 JPEG/PNG/WEBP 업로드 시 통돼지집 샘플 JSON을 반환한다")
    void parseReceipt_mockMode_success() throws Exception {
        MockMultipartFile jpegFile = new MockMultipartFile(
                "image", "receipt.jpg", "image/jpeg", VALID_JPEG_BYTES);
        MockMultipartFile pngFile = new MockMultipartFile(
                "image", "receipt.png", "image/png", VALID_PNG_BYTES);
        MockMultipartFile webpFile = new MockMultipartFile(
                "image", "receipt.webp", "image/webp", VALID_WEBP_BYTES);

        ReceiptParseResponse pngRes = receiptParseService.parseReceipt(pngFile);
        ReceiptParseResponse webpRes = receiptParseService.parseReceipt(webpFile);
        assertThat(pngRes.getPlaceName()).isEqualTo("통돼지집");
        assertThat(webpRes.getReceiptTotal()).isEqualTo(50000);

        mockMvc.perform(multipart("/api/receipts/parse").file(jpegFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeName").value("통돼지집"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("삼겹살"))
                .andExpect(jsonPath("$.items[0].qty").value(1))
                .andExpect(jsonPath("$.items[0].price").value(40000))
                .andExpect(jsonPath("$.items[1].name").value("소주"))
                .andExpect(jsonPath("$.items[1].qty").value(1))
                .andExpect(jsonPath("$.items[1].price").value(10000))
                .andExpect(jsonPath("$.receiptTotal").value(50000))
                .andExpect(jsonPath("$.adjustmentAmount").value(0))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    @DisplayName("입력 검증: 파일 누락, 빈 파일, 시그니처 위조(GIF/텍스트), 5MB 초과 시 400을 반환한다")
    void parseReceipt_invalidInput_returns400() throws Exception {
        // 1. 파일 누락
        mockMvc.perform(multipart("/api/receipts/parse"))
                .andExpect(status().isBadRequest());

        // 2. JPEG Content-Type이지만 실제 바이트는 GIF 시그니처("GIF89a")인 경우
        MockMultipartFile spoofedGif = new MockMultipartFile(
                "image", "fake.jpg", "image/jpeg", "GIF89a0000".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/receipts/parse").file(spoofedGif))
                .andExpect(status().isBadRequest());

        // 3. Content-Type(image/png)과 시그니처(JPEG)가 서로 다른 경우
        MockMultipartFile mismatchedType = new MockMultipartFile(
                "image", "mismatch.png", "image/png", VALID_JPEG_BYTES);
        mockMvc.perform(multipart("/api/receipts/parse").file(mismatchedType))
                .andExpect(status().isBadRequest());

        // 4. 5MB 초과 (5MB + 1바이트)
        byte[] oversized = new byte[(int) ReceiptParseService.MAX_IMAGE_BYTES + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        MockMultipartFile oversizedFile = new MockMultipartFile(
                "image", "huge.jpg", "image/jpeg", oversized);
        mockMvc.perform(multipart("/api/receipts/parse").file(oversizedFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("출력 검증: 메뉴 합계와 receiptTotal이 다르면 warnings에 '합계가 달라요'가 포함된다")
    void validateOutput_totalMismatch_addsWarning() {
        String mismatchJson = """
                {
                  "placeName": "통돼지집",
                  "items": [
                    { "name": "삼겹살", "qty": 1, "price": 40000 }
                  ],
                  "receiptTotal": 50000,
                  "adjustmentAmount": 0,
                  "warnings": []
                }
                """;

        ReceiptParseResponse response = receiptParseService.validateAndBuildResponse(mismatchJson);
        assertThat(response.getWarnings()).contains("합계가 달라요");
    }

    @Test
    @DisplayName("출력 검증: items 개수(>50), name 길이(>60), price 범위(0~10,000,000), qty(<1), 비정상 JSON은 502를 던진다")
    void validateOutput_outOfRangeOrMalformed_throwsReceiptParseException() throws Exception {
        // 1. 깨진 JSON -> HTTP 502 + "영수증을 읽지 못했어요"
        GeminiClient brokenClient = new GeminiClient(false, "gemini-test", "key") {
            @Override
            public String parseReceiptImage(byte[] imageBytes, String mimeType) {
                return "not-a-json";
            }
        };
        ReceiptParseService brokenService = new ReceiptParseService(brokenClient, objectMapper);
        MockMvc brokenMvc = MockMvcBuilders.standaloneSetup(new ReceiptController(brokenService, rateLimiter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        MockMultipartFile validJpeg = new MockMultipartFile(
                "image", "receipt.jpg", "image/jpeg", VALID_JPEG_BYTES);
        brokenMvc.perform(multipart("/api/receipts/parse").file(validJpeg))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("영수증을 읽지 못했어요"));

        // 2. items 51개 초과
        String fiftyOneItems = IntStream.range(0, 51)
                .mapToObj(i -> "{\"name\":\"메뉴" + i + "\",\"qty\":1,\"price\":1000}")
                .collect(Collectors.joining(",", "{\"items\":[", "],\"receiptTotal\":51000,\"adjustmentAmount\":0}"));
        assertThatThrownBy(() -> receiptParseService.validateAndBuildResponse(fiftyOneItems))
                .isInstanceOf(ReceiptParseException.class);

        // 3. name 61자 초과
        String longName = "가".repeat(61);
        String longNameJson = """
                {"items":[{"name":"%s","qty":1,"price":10000}],"receiptTotal":10000,"adjustmentAmount":0}
                """.formatted(longName);
        assertThatThrownBy(() -> receiptParseService.validateAndBuildResponse(longNameJson))
                .isInstanceOf(ReceiptParseException.class);

        // 4. price 범위 위반 (음수 및 10,000,000 초과, 소수점)
        String negativePriceJson = """
                {"items":[{"name":"메뉴","qty":1,"price":-100}],"receiptTotal":0,"adjustmentAmount":0}
                """;
        String overMaxPriceJson = """
                {"items":[{"name":"메뉴","qty":1,"price":10000001}],"receiptTotal":10000001,"adjustmentAmount":0}
                """;
        String floatPriceJson = """
                {"items":[{"name":"메뉴","qty":1,"price":1000.5}],"receiptTotal":1000,"adjustmentAmount":0}
                """;
        assertThatThrownBy(() -> receiptParseService.validateAndBuildResponse(negativePriceJson))
                .isInstanceOf(ReceiptParseException.class);
        assertThatThrownBy(() -> receiptParseService.validateAndBuildResponse(overMaxPriceJson))
                .isInstanceOf(ReceiptParseException.class);
        assertThatThrownBy(() -> receiptParseService.validateAndBuildResponse(floatPriceJson))
                .isInstanceOf(ReceiptParseException.class);

        // 5. qty 1 미만
        String zeroQtyJson = """
                {"items":[{"name":"메뉴","qty":0,"price":5000}],"receiptTotal":5000,"adjustmentAmount":0}
                """;
        assertThatThrownBy(() -> receiptParseService.validateAndBuildResponse(zeroQtyJson))
                .isInstanceOf(ReceiptParseException.class);
    }

    @Test
    @DisplayName("호출 횟수 제한: 동일 IP에서 시간당 20회 초과 시 429를 반환하고 1시간 뒤 해제된다")
    void rateLimiter_exceeds20PerHour_returns429() throws Exception {
        Instant baseTime = Instant.parse("2026-11-05T10:00:00Z");
        final Instant[] current = new Instant[]{baseTime};
        Clock mutableClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return current[0];
            }
        };

        ReceiptRateLimiter customLimiter = new ReceiptRateLimiter(mutableClock);
        for (int i = 0; i < 20; i++) {
            customLimiter.checkAndRecord("203.0.113.10");
        }

        // 21번째 호출은 429 예외 발생
        assertThatThrownBy(() -> customLimiter.checkAndRecord("203.0.113.10"))
                .isInstanceOf(RateLimitExceededException.class);

        // 다른 IP는 정상 통과
        customLimiter.checkAndRecord("203.0.113.11");

        // 1시간 + 1초 경과 후에는 다시 호출 가능
        current[0] = baseTime.plus(Duration.ofHours(1).plusSeconds(1));
        customLimiter.checkAndRecord("203.0.113.10");
    }
}

