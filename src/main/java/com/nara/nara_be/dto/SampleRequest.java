package com.nara.nara_be.dto;

import com.nara.nara_be.domain.Sample;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SampleRequest {

    private String name;
    private String description;

    public Sample toEntity() {
        return Sample.builder()
                .name(name)
                .description(description)
                .build();
    }
}
