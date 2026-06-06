package com.interview.service.questionbank;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class QuestionBankFallbackTerms {

    private static final Pattern TERM_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}_+#.-]{2,}");
    private static final int MAX_TERMS = 8;

    List<String> from(String query) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher matcher = TERM_PATTERN.matcher(query != null ? query : "");
        while (matcher.find() && terms.size() < MAX_TERMS) {
            terms.add(matcher.group());
        }
        if (terms.isEmpty()) terms.add(query != null ? query : "");
        return new ArrayList<>(terms);
    }
}
