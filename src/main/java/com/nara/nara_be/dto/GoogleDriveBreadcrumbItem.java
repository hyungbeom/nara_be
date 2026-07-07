package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoogleDriveBreadcrumbItem {

    private final String id;
    private final String name;
}
