package com.nara.nara_be.dto;

import com.nara.nara_be.domain.Sample;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SampleResponse {

    private Long id;
    private String name;
    private String description;

    public static SampleResponse from(Sample sample) {
        return SampleResponse.builder()
                .id(sample.getId())
                .name(sample.getName())
                .description(sample.getDescription())
                .build();
    }
}
