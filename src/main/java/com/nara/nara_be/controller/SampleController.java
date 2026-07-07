package com.nara.nara_be.controller;

import com.nara.nara_be.common.response.ApiResponse;
import com.nara.nara_be.dto.SampleRequest;
import com.nara.nara_be.dto.SampleResponse;
import com.nara.nara_be.service.SampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    @GetMapping
    public ApiResponse<List<SampleResponse>> findAll() {
        return ApiResponse.success(sampleService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SampleResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(sampleService.findById(id));
    }

    @PostMapping
    public ApiResponse<SampleResponse> create(@RequestBody SampleRequest request) {
        return ApiResponse.success(sampleService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SampleResponse> update(@PathVariable Long id, @RequestBody SampleRequest request) {
        return ApiResponse.success(sampleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sampleService.delete(id);
        return ApiResponse.success("deleted", null);
    }
}
