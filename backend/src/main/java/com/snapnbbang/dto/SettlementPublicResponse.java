package com.snapnbbang.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
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
public class SettlementPublicResponse {

    private String title;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate settledOn;

    private List<ParticipantSummary> participants;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ParticipantSummary {
        private Long id;
        private String name;

        @JsonProperty("isHost")
        private boolean isHost;

        private boolean claimed;

        @JsonProperty("isHost")
        public boolean isHost() {
            return isHost;
        }
    }
}
