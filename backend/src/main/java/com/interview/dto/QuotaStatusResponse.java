package com.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class QuotaStatusResponse {
    private LocalDate date;
    private List<Item> items = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String quotaType;
        private String label;
        private Integer used;
        private Integer limit;
        private Integer remaining;
    }
}
