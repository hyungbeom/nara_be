package com.nara.nara_be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotebookLMDeepResearchAsyncService {

    private final NotebookLMService notebookLMService;

    @Async("notebooklmExecutor")
    public void start(String notebookId, String projectName) {
        try {
            notebookLMService.runDeepResearch(notebookId, projectName);
        } catch (RuntimeException ex) {
            log.error("NotebookLM Deep Research 실패: notebookId={}, projectName={}", notebookId, projectName, ex);
        }
    }

    @Async("notebooklmExecutor")
    public void importSources(String notebookId, String projectName) {
        try {
            notebookLMService.importCompletedResearchSources(notebookId);
            log.info("NotebookLM Deep Research 소스 import 완료: notebookId={}, projectName={}", notebookId, projectName);
        } catch (RuntimeException ex) {
            log.error("NotebookLM Deep Research 소스 import 실패: notebookId={}, projectName={}", notebookId, projectName, ex);
        }
    }
}
