package com.interview.dto.questionbank;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionBankPageResponse<T> {
    private long total;
    private int page;
    private int size;
    private List<T> items = new ArrayList<>();

    public static <T> QuestionBankPageResponse<T> of(long total, int page, int size, List<T> items) {
        QuestionBankPageResponse<T> response = new QuestionBankPageResponse<>();
        response.setTotal(total);
        response.setPage(page);
        response.setSize(size);
        response.setItems(items != null ? items : List.of());
        return response;
    }
}
