package com.nara.nara_be.service;

import com.nara.nara_be.dto.SampleRequest;
import com.nara.nara_be.dto.SampleResponse;

import java.util.List;

public interface SampleService {

    List<SampleResponse> findAll();

    SampleResponse findById(Long id);

    SampleResponse create(SampleRequest request);

    SampleResponse update(Long id, SampleRequest request);

    void delete(Long id);
}
