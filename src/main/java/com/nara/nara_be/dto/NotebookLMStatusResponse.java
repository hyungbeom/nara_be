package com.nara.nara_be.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotebookLMStatusResponse {

    private final boolean enabled;
    private final boolean pythonAvailable;
    private final boolean authenticated;
    private final boolean loginInProgress;
    private final String accountEmail;
    private final String homePath;
    private final String message;
    private final boolean headlessReady;
}
