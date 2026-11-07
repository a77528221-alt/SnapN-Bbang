package com.snapnbbang.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapnbbang.common.ReceiptParseException;
import com.snapnbbang.dto.ReceiptParseResponse;
import com.snapnbbang.dto.ReceiptParseResponse.ReceiptItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReceiptParseService {

    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5MB
    public static final int MAX_ITEMS_COUNT = 50;
    public static final int MAX_ITEM_NAME_LENGTH = 60;
    public static final int MIN_ITEM_PRICE = 0;
    public static final int MAX_ITEM_PRICE = 10_000_000;
    public static final int MIN_ITEM_QTY = 1;
    public static final String WARNING_TOTAL_MISMATCH = "합계가 달라요";

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public ReceiptParseService(GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 업로드된 영수증 이미지를 메모리에서 검증한 뒤 GeminiClient를 호출하고,
     * 응답 JSON 구조와 범위를 검증하여 반환한다.
     */
    public ReceiptParseResponse parseReceipt(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("영수증 이미지가 필요해요.");
        }

        if (image.getSize() <= 0 || image.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("이미지 크기는 5MB 이하여야 해요.");
        }

        String declaredContentType = image.getContentType();
        if (declaredContentType != null && !declaredContentType.isBlank()) {
            String normalized = declaredContentType.toLowerCase(Locale.ROOT).trim();
            if (!ALLOWED_CONTENT_TYPES.contains(normalized)
                    && !"application/octet-stream".equals(normalized)) {
                throw new IllegalArgumentException("지원하지 않는 이미지 형식이에요.");
            }
        }

        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("이미지를 읽을 수 없어요.");
        }

        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("이미지 크기는 5MB 이하여야 해요.");
        }

        String detectedMimeType = detectMimeTypeBySignature(bytes);
        if (detectedMimeType == null) {
            throw new IllegalArgumentException("JPEG, PNG, WEBP 형식의 이미지만 업로드할 수 있어요.");
        }

        if (declaredContentType != null && !declaredContentType.isBlank()) {
            String normalized = declaredContentType.toLowerCase(Locale.ROOT).trim();
            if ("image/jpg".equals(normalized)) {
                normalized = "image/jpeg";
            }
            if (!"application/octet-stream".equals(normalized) && !detectedMimeType.equals(normalized)) {
                throw new IllegalArgumentException("파일 확장자/타입과 실제 이미지 시그니처가 일치하지 않아요.");
            }
        }

        String rawModelJson = geminiClient.parseReceiptImage(bytes, detectedMimeType);
        return validateAndBuildResponse(rawModelJson);
    }

    /**
     * 파일 시그니처(매직 바이트)를 확인하여 MIME 타입을 판별한다.
     * - JPEG: FF D8 FF
     * - PNG : 89 50 4E 47 0D 0A 1A 0A
     * - WEBP: 52 49 46 46 ("RIFF") ... 57 45 42 50 ("WEBP")
     */
    public String detectMimeTypeBySignature(byte[] bytes) {
        if (bytes == null || bytes.length < 3) {
            return null;
        }

        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47
                && (bytes[4] & 0xFF) == 0x0D
                && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A
                && (bytes[7] & 0xFF) == 0x0A) {
            return "image/png";
        }

        // WEBP: "RIFF" (0..3) + "WEBP" (8..11)
        if (bytes.length >= 12
                && (bytes[0] & 0xFF) == 0x52
                && (bytes[1] & 0xFF) == 0x49
                && (bytes[2] & 0xFF) == 0x46
                && (bytes[3] & 0xFF) == 0x46
                && (bytes[8] & 0xFF) == 0x57
                && (bytes[9] & 0xFF) == 0x45
                && (bytes[10] & 0xFF) == 0x42
                && (bytes[11] & 0xFF) == 0x50) {
            return "image/webp";
        }

        return null;
    }

    /**
     * 모델 응답 JSON 문자열을 파싱하고 출력 제약 조건을 검증한다.
     * 위반 시 ReceiptParseException (502 Bad Gateway, "영수증을 읽지 못했어요") 발생.
     */
    public ReceiptParseResponse validateAndBuildResponse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new ReceiptParseException();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (JsonProcessingException ex) {
            throw new ReceiptParseException();
        }

        if (root == null || !root.isObject()) {
            throw new ReceiptParseException();
        }

        // 1. placeName 검증 (문자열 또는 null)
        String placeName = null;
        JsonNode placeNameNode = root.get("placeName");
        if (placeNameNode != null && !placeNameNode.isNull()) {
            if (!placeNameNode.isTextual()) {
                throw new ReceiptParseException();
            }
            placeName = placeNameNode.asText();
        }

        // 2. items 검증 (필수 배열, 최대 50개)
        JsonNode itemsNode = root.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.size() > MAX_ITEMS_COUNT) {
            throw new ReceiptParseException();
        }

        List<ReceiptItem> items = new ArrayList<>(itemsNode.size());
        long itemsSum = 0L;

        for (JsonNode itemNode : itemsNode) {
            if (itemNode == null || !itemNode.isObject()) {
                throw new ReceiptParseException();
            }

            JsonNode nameNode = itemNode.get("name");
            if (nameNode == null || !nameNode.isTextual()) {
                throw new ReceiptParseException();
            }
            String name = nameNode.asText();
            if (name.isBlank() || name.length() > MAX_ITEM_NAME_LENGTH) {
                throw new ReceiptParseException();
            }

            JsonNode qtyNode = itemNode.get("qty");
            if (qtyNode == null || !qtyNode.isIntegralNumber() || !qtyNode.canConvertToInt()) {
                throw new ReceiptParseException();
            }
            int qty = qtyNode.intValue();
            if (qty < MIN_ITEM_QTY) {
                throw new ReceiptParseException();
            }

            JsonNode priceNode = itemNode.get("price");
            if (priceNode == null || !priceNode.isIntegralNumber() || !priceNode.canConvertToInt()) {
                throw new ReceiptParseException();
            }
            int price = priceNode.intValue();
            if (price < MIN_ITEM_PRICE || price > MAX_ITEM_PRICE) {
                throw new ReceiptParseException();
            }

            items.add(new ReceiptItem(name, qty, price));
            itemsSum += price;
        }

        // 3. receiptTotal 검증 (정수 또는 null)
        Integer receiptTotal = null;
        JsonNode receiptTotalNode = root.get("receiptTotal");
        if (receiptTotalNode != null && !receiptTotalNode.isNull()) {
            if (!receiptTotalNode.isIntegralNumber() || !receiptTotalNode.canConvertToInt()) {
                throw new ReceiptParseException();
            }
            int totalVal = receiptTotalNode.intValue();
            if (totalVal < 0) {
                throw new ReceiptParseException();
            }
            receiptTotal = totalVal;
        }

        // 4. adjustmentAmount 검증 (정수, 없거나 null이면 0)
        int adjustmentAmount = 0;
        JsonNode adjustmentNode = root.get("adjustmentAmount");
        if (adjustmentNode != null && !adjustmentNode.isNull()) {
            if (!adjustmentNode.isIntegralNumber() || !adjustmentNode.canConvertToInt()) {
                throw new ReceiptParseException();
            }
            adjustmentAmount = adjustmentNode.intValue();
        }

        // 5. warnings 구성 및 합계 불일치 경고 추가
        List<String> warnings = new ArrayList<>();
        JsonNode warningsNode = root.get("warnings");
        if (warningsNode != null && !warningsNode.isNull()) {
            if (!warningsNode.isArray()) {
                throw new ReceiptParseException();
            }
            for (JsonNode w : warningsNode) {
                if (!w.isTextual()) {
                    throw new ReceiptParseException();
                }
                warnings.add(w.asText());
            }
        }

        if (receiptTotal != null && (itemsSum + adjustmentAmount) != receiptTotal.longValue()) {
            if (!warnings.contains(WARNING_TOTAL_MISMATCH)) {
                warnings.add(WARNING_TOTAL_MISMATCH);
            }
        }

        return new ReceiptParseResponse(
                placeName,
                items,
                receiptTotal,
                adjustmentAmount,
                warnings
        );
    }
}

