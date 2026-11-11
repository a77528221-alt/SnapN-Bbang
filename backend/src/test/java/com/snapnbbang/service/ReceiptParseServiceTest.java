package com.snapnbbang.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapnbbang.common.GlobalExceptionHandler;
import com.snapnbbang.common.RateLimitExceededException;
import com.snapnbbang.common.ReceiptParseException;
import com.snapnbbang.controller.ReceiptController;
import com.snapnbbang.dto.ReceiptParseResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
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
    @DisplayName("GEMINI_MOCK=true 모드에서 키와 모델이 비어 있어도 Gemini를 호출하지 않고 통돼지집 샘플 JSON을 반환한다")
    void parseReceipt_mockMode_emptyKeyAndModel_doesNotCallExternalServer() throws Exception {
        AtomicInteger serverHitCount = new AtomicInteger(0);
        ClientHttpRequestFactory spyFactory = (uri, httpMethod) -> {
            serverHitCount.incrementAndGet();
            throw new AssertionError("GEMINI_MOCK=true 일 때는 외부 HTTP 요청이 생성되면 안 됩니다.");
        };

        GeminiClient clientWithEmptyKeyAndModel = new GeminiClient(
                true, "", "", "https://generativelanguage.googleapis.com", spyFactory, objectMapper);
        ReceiptParseService service = new ReceiptParseService(clientWithEmptyKeyAndModel, objectMapper);

        MockMultipartFile jpegFile = new MockMultipartFile(
                "image", "receipt.jpg", "image/jpeg", VALID_JPEG_BYTES);
        MockMultipartFile pngFile = new MockMultipartFile(
                "image", "receipt.png", "image/png", VALID_PNG_BYTES);
        MockMultipartFile webpFile = new MockMultipartFile(
                "image", "receipt.webp", "image/webp", VALID_WEBP_BYTES);

        ReceiptParseResponse pngRes = service.parseReceipt(pngFile);
        ReceiptParseResponse webpRes = service.parseReceipt(webpFile);
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

        assertThat(serverHitCount.get()).isZero();
    }

    @Test
    @DisplayName("실제 Gemini 응답 구조(thoughtSignature·usageMetadata·modelVersion 포함)와 ```json 코드블록을 파싱한다")
    void extractModelText_realResponseStructureWithCodeFence_success() {
        String geminiApiEnvelope = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "```json\\n{\\n  \\"placeName\\": \\"역전할머니맥주\\",\\n  \\"items\\": [\\n    { \\"name\\": \\"먹태\\", \\"qty\\": 1, \\"price\\": 15000 },\\n    { \\"name\\": \\"생맥주\\", \\"qty\\": 3, \\"price\\": 12000 }\\n  ],\\n  \\"receiptTotal\\": 27000,\\n  \\"adjustmentAmount\\": 0,\\n  \\"warnings\\": []\\n}\\n```",
                            "thoughtSignature": "EtcDCtQDAWkUfRNhNt75l+fK5/UEjkKJym0pkbdqoqZuESHDvIxN3YEGM4sisiJPSwfKsxHiUebnIW9RiNXLErlRiNbmZHyxufLXLPHfkWVPEwjMUXTycklDm8V0No2T0l4EaqFwJu66eY1jToGJlwhNgLl9BU7HbAy7kX3TvqTxa81nB8NLAdkUDBzA4pc9aEpp5nzVswZFXCVwtwcAqzSw1K8N5FV6ERon5qOQEhr1KMj/XfzAc/dV5IcjGqN34LTdEcuG5OgJ5aGETOvpS6Hm6+SXnG8LCKIMm58/OF51BBq0g1Sw+sbjSAxrg6vEIIkvNfk+0uGAZst2m4jt9+cG2AcuDy+6ZJLGsNneEeezvIy6+bvGFKtXO/JtDwcyOX7kVbrkaMkixEPXGD4gvATnNRMuU6ZTx+FOeF0zoaMNhke8xpkgQRao69B7bPhLpSbbVRySFScMOWRtWS4fUULX/cS5fMACsxPNnParGlL/iZQN0CItvUd/XxJKRn3iQRIOf9jg+w4sSFVS8mm26qnptDIcp1DXoaMONM+wdpOWVnp43xjyicq50Srsh8YYXkjwlHw773DKU5ORI4FosGCTqfYGbrmPNvdy2Ss+nOPGfNaJNFY8qvxF"
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP",
                      "index": 0
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 5,
                    "candidatesTokenCount": 1,
                    "totalTokenCount": 121,
                    "promptTokensDetails": [
                      {
                        "modality": "TEXT",
                        "tokenCount": 5
                      }
                    ],
                    "thoughtsTokenCount": 115,
                    "serviceTier": "standard"
                  },
                  "modelVersion": "gemini-3.6-flash",
                  "responseId": "VAPwapnYCM7Y9tMPq7eM8A8"
                }
                """;

        String extractedJson = mockGeminiClient.extractModelText(geminiApiEnvelope);
        ReceiptParseResponse response = receiptParseService.validateAndBuildResponse(extractedJson);

        assertThat(response.getPlaceName()).isEqualTo("역전할머니맥주");
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getName()).isEqualTo("먹태");
        assertThat(response.getItems().get(0).getPrice()).isEqualTo(15000);
        assertThat(response.getReceiptTotal()).isEqualTo(27000);
        assertThat(response.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("가짜 서버 검증: 요청 형식(헤더 키, URL ?key= 미포함, inline_data/text 본문) 및 401·403·429·5xx·타임아웃 매핑")
    void geminiClient_fakeServer_requestFormatAndErrorStatusMapping() throws Exception {
        AtomicInteger nextStatus = new AtomicInteger(200);
        AtomicBoolean simulateTimeout = new AtomicBoolean(false);
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        AtomicReference<HttpMethod> capturedMethod = new AtomicReference<>();
        AtomicReference<HttpHeaders> capturedHeaders = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        String sampleSuccessEnvelope = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "```json\\n{\\"placeName\\":\\"일미집\\",\\"items\\":[{\\"name\\":\\"삼겹살\\",\\"qty\\":2,\\"price\\":30000}],\\"receiptTotal\\":30000,\\"adjustmentAmount\\":0,\\"warnings\\":[]}\\n```",
                            "thoughtSignature": "sig-123"
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP",
                      "index": 0
                    }
                  ],
                  "modelVersion": "gemini-3.6-flash"
                }
                """;

        ClientHttpRequestFactory fakeServerFactory = (uri, httpMethod) -> new ClientHttpRequest() {
            private final HttpHeaders headers = new HttpHeaders();
            private final ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();

            @Override
            public ClientHttpResponse execute() throws IOException {
                capturedUri.set(uri);
                capturedMethod.set(httpMethod);
                capturedHeaders.set(headers);
                capturedBody.set(bodyStream.toString(StandardCharsets.UTF_8));

                if (simulateTimeout.get()) {
                    throw new SocketTimeoutException("Read timed out");
                }

                int status = nextStatus.get();
                byte[] responseBytes = (status == 200)
                        ? sampleSuccessEnvelope.getBytes(StandardCharsets.UTF_8)
                        : "{\"error\":{\"message\":\"internal secret reason\"}}".getBytes(StandardCharsets.UTF_8);

                return new ClientHttpResponse() {
                    @Override
                    public HttpStatusCode getStatusCode() {
                        return HttpStatusCode.valueOf(status);
                    }

                    @Override
                    public String getStatusText() {
                        return HttpStatus.valueOf(status).getReasonPhrase();
                    }

                    @Override
                    public void close() {
                    }

                    @Override
                    public InputStream getBody() {
                        return new ByteArrayInputStream(responseBytes);
                    }

                    @Override
                    public HttpHeaders getHeaders() {
                        HttpHeaders respHeaders = new HttpHeaders();
                        respHeaders.add(HttpHeaders.CONTENT_TYPE, "application/json");
                        return respHeaders;
                    }
                };
            }

            @Override
            public OutputStream getBody() {
                return bodyStream;
            }

            @Override
            public HttpMethod getMethod() {
                return httpMethod;
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };

        String aqKey = "AQ.Ab8RN6L_new_format_test_key_98765";
        GeminiClient liveClient = new GeminiClient(
                false,
                "gemini-3.6-flash",
                aqKey,
                "https://generativelanguage.googleapis.com",
                fakeServerFactory,
                objectMapper
        );
        ReceiptParseService liveService = new ReceiptParseService(liveClient, objectMapper);
        MockMvc liveMvc = MockMvcBuilders.standaloneSetup(
                        new ReceiptController(liveService, new ReceiptRateLimiter()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        MockMultipartFile jpegFile = new MockMultipartFile(
                "image", "receipt.jpg", "image/jpeg", VALID_JPEG_BYTES);

        // 1. 200 OK 및 요청 포맷 검증
        nextStatus.set(200);
        liveMvc.perform(multipart("/api/receipts/parse").file(jpegFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeName").value("일미집"))
                .andExpect(jsonPath("$.items[0].name").value("삼겹살"))
                .andExpect(jsonPath("$.items[0].price").value(30000));

        assertThat(capturedMethod.get()).isEqualTo(HttpMethod.POST);
        assertThat(capturedUri.get().toString())
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent");
        assertThat(capturedUri.get().getRawQuery()).isNull();
        assertThat(capturedHeaders.get().getFirst("x-goog-api-key")).isEqualTo(aqKey);
        assertThat(capturedHeaders.get().getFirst(HttpHeaders.CONTENT_TYPE)).startsWith("application/json");

        JsonNode sentJson = objectMapper.readTree(capturedBody.get());
        JsonNode parts = sentJson.path("contents").get(0).path("parts");
        assertThat(parts.get(0).path("inline_data").path("mime_type").asText()).isEqualTo("image/jpeg");
        assertThat(parts.get(0).path("inline_data").path("data").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(VALID_JPEG_BYTES));
        assertThat(parts.get(1).path("text").asText()).isEqualTo(GeminiClient.RECEIPT_PARSE_PROMPT);

        // 2. 401 Unauthorized -> 500 "서버 설정 오류"
        nextStatus.set(401);
        liveMvc.perform(multipart("/api/receipts/parse").file(jpegFile))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 설정 오류"));

        // 3. 403 Forbidden -> 500 "서버 설정 오류"
        nextStatus.set(403);
        liveMvc.perform(multipart("/api/receipts/parse").file(jpegFile))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 설정 오류"));

        // 4. 429 Too Many Requests -> 429
        nextStatus.set(429);
        liveMvc.perform(multipart("/api/receipts/parse").file(jpegFile))
                .andExpect(status().isTooManyRequests());

        // 5. 503 Service Unavailable -> 502 "영수증을 읽지 못했어요"
        nextStatus.set(503);
        liveMvc.perform(multipart("/api/receipts/parse").file(jpegFile))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("영수증을 읽지 못했어요"));

        // 6. 응답 타임아웃(SocketTimeoutException) -> 502 "영수증을 읽지 못했어요"
        nextStatus.set(200);
        simulateTimeout.set(true);
        liveMvc.perform(multipart("/api/receipts/parse").file(jpegFile))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("영수증을 읽지 못했어요"));
    }

    @Test
    @DisplayName("진단 로그 검증: 단계·상태코드·finishReason·규칙만 기록하고 API 키·이미지·본문·메뉴명·금액·URL은 절대 남기지 않는다")
    void diagnosticLogging_recordsOnlyAllowedFieldsAndNeverLeaksSensitiveInfo() throws Exception {
        Logger geminiLogger = (Logger) LoggerFactory.getLogger(GeminiClient.class);
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(ReceiptParseService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        geminiLogger.addAppender(appender);
        serviceLogger.addAppender(appender);

        String secretApiKey = "AQ.SECRET_KEY_NEVER_LOG_99999";
        String secretPlaceName = "비밀단골식당_노출금지";
        String secretMenuName = "한우특수부위모둠_민감메뉴명";
        int secretMenuPrice = 98_765_432;
        String secretModelRawText = "민감한모델원문텍스트_절대로그금지";
        String imageBase64 = Base64.getEncoder().encodeToString(VALID_JPEG_BYTES);

        AtomicInteger statusHolder = new AtomicInteger(200);
        AtomicReference<String> bodyHolder = new AtomicReference<>("");
        AtomicBoolean throwTimeout = new AtomicBoolean(false);
        AtomicBoolean throwConnectError = new AtomicBoolean(false);

        ClientHttpRequestFactory diagFactory = (uri, httpMethod) -> new ClientHttpRequest() {
            private final HttpHeaders headers = new HttpHeaders();
            private final ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();

            @Override
            public ClientHttpResponse execute() throws IOException {
                if (throwTimeout.get()) {
                    throw new SocketTimeoutException("Read timed out from https://generativelanguage.googleapis.com?key=" + secretApiKey);
                }
                if (throwConnectError.get()) {
                    throw new IOException("Connect failed to https://generativelanguage.googleapis.com?key=" + secretApiKey);
                }

                int status = statusHolder.get();
                byte[] respBytes = bodyHolder.get().getBytes(StandardCharsets.UTF_8);
                return new ClientHttpResponse() {
                    @Override
                    public HttpStatusCode getStatusCode() {
                        return HttpStatusCode.valueOf(status);
                    }

                    @Override
                    public String getStatusText() {
                        return HttpStatus.valueOf(status).getReasonPhrase();
                    }

                    @Override
                    public void close() {
                    }

                    @Override
                    public InputStream getBody() {
                        return new ByteArrayInputStream(respBytes);
                    }

                    @Override
                    public HttpHeaders getHeaders() {
                        HttpHeaders h = new HttpHeaders();
                        h.add(HttpHeaders.CONTENT_TYPE, "application/json");
                        return h;
                    }
                };
            }

            @Override
            public OutputStream getBody() {
                return bodyStream;
            }

            @Override
            public HttpMethod getMethod() {
                return httpMethod;
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };

        try {
            GeminiClient diagClient = new GeminiClient(
                    false,
                    "gemini-3.6-flash",
                    secretApiKey,
                    "https://generativelanguage.googleapis.com",
                    diagFactory,
                    objectMapper
            );
            ReceiptParseService diagService = new ReceiptParseService(diagClient, objectMapper);
            MockMultipartFile jpegFile = new MockMultipartFile(
                    "image", "receipt.jpg", "image/jpeg", VALID_JPEG_BYTES);

            // 1) 시간 초과
            throwTimeout.set(true);
            assertThatThrownBy(() -> diagService.parseReceipt(jpegFile))
                    .isInstanceOf(ReceiptParseException.class);
            throwTimeout.set(false);

            // 2) 연결 실패
            throwConnectError.set(true);
            assertThatThrownBy(() -> diagService.parseReceipt(jpegFile))
                    .isInstanceOf(ReceiptParseException.class);
            throwConnectError.set(false);

            // 3) HTTP 503 오류
            statusHolder.set(503);
            bodyHolder.set("{\"error\":\"" + secretModelRawText + "\"}");
            assertThatThrownBy(() -> diagService.parseReceipt(jpegFile))
                    .isInstanceOf(ReceiptParseException.class);

            // 4) 응답 구조 불일치 (SAFETY로 인해 content.parts가 비어 있는 경우)
            statusHolder.set(200);
            bodyHolder.set("""
                    {
                      "candidates": [
                        {
                          "content": { "parts": [] },
                          "finishReason": "SAFETY"
                        }
                      ]
                    }
                    """);
            assertThatThrownBy(() -> diagService.parseReceipt(jpegFile))
                    .isInstanceOf(ReceiptParseException.class);

            // 5) JSON 파싱 실패 (MAX_TOKENS로 인해 모델 텍스트가 잘린 경우)
            bodyHolder.set("""
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              { "text": "{\\"placeName\\":\\"%s\\", \\"broken\\": \\"%s" }
                            ]
                          },
                          "finishReason": "MAX_TOKENS"
                        }
                      ]
                    }
                    """.formatted(secretPlaceName, secretModelRawText));
            assertThatThrownBy(() -> diagService.parseReceipt(jpegFile))
                    .isInstanceOf(ReceiptParseException.class);

            // 6) 출력 검증 실패 (가격 범위 위반: 98,765,432원)
            bodyHolder.set("""
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "text": "{\\"placeName\\":\\"%s\\",\\"items\\":[{\\"name\\":\\"%s\\",\\"qty\\":1,\\"price\\":%d}],\\"receiptTotal\\":%d,\\"adjustmentAmount\\":0}"
                              }
                            ]
                          },
                          "finishReason": "STOP"
                        }
                      ]
                    }
                    """.formatted(secretPlaceName, secretMenuName, secretMenuPrice, secretMenuPrice));
            assertThatThrownBy(() -> diagService.parseReceipt(jpegFile))
                    .isInstanceOf(ReceiptParseException.class);

            List<String> logMessages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(logMessages).contains(
                    "단계=시간 초과, 상태코드=없음, finishReason=없음",
                    "단계=연결 실패, 상태코드=없음, finishReason=없음",
                    "단계=HTTP 오류, 상태코드=503, finishReason=없음",
                    "단계=응답 구조 불일치, 상태코드=200, finishReason=SAFETY",
                    "단계=JSON 파싱 실패, 상태코드=200, finishReason=MAX_TOKENS",
                    "단계=출력 검증 실패, 상태코드=200, finishReason=STOP, 규칙=가격 범위"
            );

            // 어떤 로그 줄에도 민감 값(API 키, 이미지 base64, URL, 모델 원문, 가게명, 메뉴명, 금액)이 없어야 함
            for (String line : logMessages) {
                assertThat(line)
                        .doesNotContain(secretApiKey)
                        .doesNotContain(imageBase64)
                        .doesNotContain("generativelanguage.googleapis.com")
                        .doesNotContain("/v1beta/models")
                        .doesNotContain(secretModelRawText)
                        .doesNotContain(secretPlaceName)
                        .doesNotContain(secretMenuName)
                        .doesNotContain(String.valueOf(secretMenuPrice));
            }
        } finally {
            geminiLogger.detachAppender(appender);
            serviceLogger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("입력 검증: 파일 누락, 빈 파일, 시그니처 위조(GIF/텍스트), 5MB 초과 시 400을 반환한다")
    void parseReceipt_invalidInput_returns400() throws Exception {
        mockMvc.perform(multipart("/api/receipts/parse"))
                .andExpect(status().isBadRequest());

        MockMultipartFile spoofedGif = new MockMultipartFile(
                "image", "fake.jpg", "image/jpeg", "GIF89a0000".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/receipts/parse").file(spoofedGif))
                .andExpect(status().isBadRequest());

        MockMultipartFile mismatchedType = new MockMultipartFile(
                "image", "mismatch.png", "image/png", VALID_JPEG_BYTES);
        mockMvc.perform(multipart("/api/receipts/parse").file(mismatchedType))
                .andExpect(status().isBadRequest());

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

        String fiftyOneItems = IntStream.range(0, 51)
                .mapToObj(i -> "{\"name\":\"메뉴" + i + "\",\"qty\":1,\"price\":1000}")
                .collect(Collectors.joining(",", "{\"items\":[", "],\"receiptTotal\":51000,\"adjustmentAmount\":0}"));
        assertThatThrownBy(() -> receiptParseService.validateAndBuildResponse(fiftyOneItems))
                .isInstanceOf(ReceiptParseException.class);

        String longName = "가".repeat(61);
        String longNameJson = """
                {"items":[{"name":"%s","qty":1,"price":10000}],"receiptTotal":10000,"adjustmentAmount":0}
                """.formatted(longName);
        assertThatThrownBy(() -> receiptParseService.validateAndBuildResponse(longNameJson))
                .isInstanceOf(ReceiptParseException.class);

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

        String zeroQtyJson = """
                {"items":[{"name":"메뉴","qty":0,"price":5000}],"receiptTotal":5000,"adjustmentAmount":0}
                """;
        assertThatThrownBy(() -> receiptParseService.validateAndBuildResponse(zeroQtyJson))
                .isInstanceOf(ReceiptParseException.class);
    }

    @Test
    @DisplayName("호출 횟수 제한: 동일 IP에서 시간당 20회 초과 시 429를 반환하고 1시간 뒤 해제된다")
    void rateLimiter_exceeds20PerHour_returns429() {
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

        assertThatThrownBy(() -> customLimiter.checkAndRecord("203.0.113.10"))
                .isInstanceOf(RateLimitExceededException.class);

        customLimiter.checkAndRecord("203.0.113.11");

        current[0] = baseTime.plus(Duration.ofHours(1).plusSeconds(1));
        customLimiter.checkAndRecord("203.0.113.10");
    }
}
