package com.tutorial.testlogs.controller;

import com.tutorial.testlogs.service.ExampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/examples", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExampleController {

    private final ExampleService exampleService;

    @PostMapping(path = "/process", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, List<String>> process(@RequestBody List<String> input) {
        return exampleService.process(input);
    }
}

