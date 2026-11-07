package com.snapnbbang.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReceiptParseResponse {

    private final String placeName;
    private final List<ReceiptItem> items;
    private final Integer receiptTotal;
    private final int adjustmentAmount;
    private final List<String> warnings;

    @Getter
    @AllArgsConstructor
    public static class ReceiptItem {
        private final String name;
        private final int qty;
        private final int price;
    }
}

