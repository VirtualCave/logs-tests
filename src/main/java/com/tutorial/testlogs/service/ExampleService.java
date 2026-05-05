package com.tutorial.testlogs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExampleService {

    private static final String KEY_SHORT = "SHORTCHAR";
    private static final String KEY_LONG = "LONGCHAR";
    private static final String KEY_NUMERIC = "NUMERIC";

    public Map<String, List<String>> process(List<String> input) {
        log.info("Processing input with {} items", input.size());

        if (input.isEmpty()) {
            log.warn("Received empty input");
            return createEmptyResult();
        }

        Map<String, List<String>> result = input.stream()
                .peek(this::logClassification)
                .collect(groupingBy(this::classify, HashMap::new, toList()));

        ensureAllCategories(result);

        logCompletion(result);
        return result;
    }

    private String classify(String item) {
        return isNumeric(item) ? KEY_NUMERIC : (item.length() < 10 ? KEY_SHORT : KEY_LONG);
    }

    private void logClassification(String item) {
        String category = classify(item);
        switch (category) {
            case KEY_NUMERIC -> log.debug("Item '{}' classified as NUMERIC", item);
            case KEY_SHORT -> log.debug("Item '{}' classified as SHORTCHAR (length={})", item, item.length());
            default -> log.debug("Item '{}' classified as LONGCHAR (length={})", item, item.length());
        }
    }

    private void logCompletion(Map<String, List<String>> result) {
        log.info("Classification complete — SHORTCHAR={}, LONGCHAR={}, NUMERIC={}",
                result.getOrDefault(KEY_SHORT, List.of()).size(),
                result.getOrDefault(KEY_LONG, List.of()).size(),
                result.getOrDefault(KEY_NUMERIC, List.of()).size());
    }

    private void ensureAllCategories(Map<String, List<String>> result) {
        result.putIfAbsent(KEY_SHORT, List.of());
        result.putIfAbsent(KEY_LONG, List.of());
        result.putIfAbsent(KEY_NUMERIC, List.of());
    }

    private Map<String, List<String>> createEmptyResult() {
        return Map.of(KEY_SHORT, List.of(), KEY_LONG, List.of(), KEY_NUMERIC, List.of());
    }

    private boolean isNumeric(String value) {
        return Optional.ofNullable(value)
                .filter(v -> !v.isBlank())
                .map(v -> {
                    try {
                        Double.parseDouble(v);
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                })
                .orElse(false);
    }
}
