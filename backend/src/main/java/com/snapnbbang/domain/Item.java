package com.snapnbbang.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Item {

    private Long id;
    private Long roundId;
    private String settlementId;
    private String name;
    private Integer qty;
    private Integer price;
}
