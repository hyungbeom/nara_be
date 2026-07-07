package com.nara.nara_be.service;

import com.nara.nara_be.dto.NotebookLMCreateProjectRequest;
import com.nara.nara_be.dto.NotebookLMCreateProjectResponse;
import com.nara.nara_be.dto.NotebookLMLoginRequest;
import com.nara.nara_be.dto.NotebookLMProjectCheckResponse;
import com.nara.nara_be.dto.NotebookLMProjectHistoryResponse;
import com.nara.nara_be.dto.NotebookLMProjectSourceContentResponse;
import com.nara.nara_be.dto.NotebookLMProjectSourcesResponse;
import com.nara.nara_be.dto.NotebookLMProjectSummaryResponse;
import com.nara.nara_be.dto.NotebookLMResearchStatusResponse;
import com.nara.nara_be.dto.NotebookLMUpdateSourceSelectionRequest;
import com.nara.nara_be.dto.NotebookLMRunStepRequest;
import com.nara.nara_be.dto.NotebookLMRunStepResultResponse;
import com.nara.nara_be.dto.NotebookLMSaveStepsRequest;
import com.nara.nara_be.dto.NotebookLMStepRunStatusResponse;
import com.nara.nara_be.dto.NotebookLMStepResponse;
import com.nara.nara_be.dto.NotebookLMStatusResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NotebookLMService {

    NotebookLMStatusResponse getStatus();

    NotebookLMProjectCheckResponse checkProjectsByName(List<String> names);

    NotebookLMCreateProjectResponse createProject(NotebookLMCreateProjectRequest request);

    NotebookLMProjectHistoryResponse getProjectHistory(String projectName);

    List<NotebookLMStepResponse> getSteps();

    List<NotebookLMStepResponse> saveSteps(NotebookLMSaveStepsRequest request);

    NotebookLMRunStepResultResponse runStep(NotebookLMRunStepRequest request);

    NotebookLMStepRunStatusResponse startStepRun(NotebookLMRunStepRequest request);

    NotebookLMStepRunStatusResponse getStepRunStatus(String projectName);

    void completeStepRun(String projectName, String step);

    NotebookLMResearchStatusResponse getResearchStatus(String projectName);

    NotebookLMProjectSourcesResponse getProjectSources(String projectName);

    NotebookLMProjectSummaryResponse getProjectSummary(String projectName);

    NotebookLMProjectSourceContentResponse getProjectSourceContent(String projectName, String sourceId);

    void updateProjectSourceSelection(NotebookLMUpdateSourceSelectionRequest request);

    void runDeepResearch(String notebookId, String projectName);

    void importCompletedResearchSources(String notebookId);

    void deleteProjectByName(String projectName);

    void startLogin(NotebookLMLoginRequest request);

    void importAuthFiles(MultipartFile masterToken, MultipartFile storageState);
}
