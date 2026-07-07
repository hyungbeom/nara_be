package com.nara.nara_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotebookLMStepRunAsyncService {

    private final NotebookLMService notebookLMService;

    @Async("notebooklmExecutor")
    public void run(String projectName, String step) {
        try {
            notebookLMService.completeStepRun(projectName, step);
        } catch (RuntimeException ex) {
            log.error("NotebookLM STEP 실행 실패: projectName={}, step={}", projectName, step, ex);
        }
    }
}
