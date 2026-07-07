package com.nara.nara_be.controller;

import com.nara.nara_be.common.response.ApiResponse;
import com.nara.nara_be.dto.NotebookLMCreateProjectRequest;
import com.nara.nara_be.dto.NotebookLMCreateProjectResponse;
import com.nara.nara_be.dto.NotebookLMLoginRequest;
import com.nara.nara_be.dto.NotebookLMProjectCheckRequest;
import com.nara.nara_be.dto.NotebookLMProjectCheckResponse;
import com.nara.nara_be.dto.NotebookLMProjectHistoryResponse;
import com.nara.nara_be.dto.NotebookLMProjectSourcesResponse;
import com.nara.nara_be.dto.NotebookLMProjectSourceContentResponse;
import com.nara.nara_be.dto.NotebookLMProjectSummaryResponse;
import com.nara.nara_be.dto.NotebookLMResearchStatusResponse;
import com.nara.nara_be.dto.NotebookLMUpdateSourceSelectionRequest;
import com.nara.nara_be.dto.NotebookLMRunStepRequest;
import com.nara.nara_be.dto.NotebookLMRunStepResultResponse;
import com.nara.nara_be.dto.NotebookLMSaveStepsRequest;
import com.nara.nara_be.dto.NotebookLMStepRunStatusResponse;
import com.nara.nara_be.dto.NotebookLMStepResponse;
import com.nara.nara_be.dto.NotebookLMStatusResponse;
import com.nara.nara_be.service.NotebookLMService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/notebooklm")
@RequiredArgsConstructor
public class NotebookLMController {

    private final NotebookLMService notebookLMService;

    @GetMapping("/status")
    public ApiResponse<NotebookLMStatusResponse> getStatus() {
        return ApiResponse.success(notebookLMService.getStatus());
    }

    @PostMapping("/projects/check")
    public ApiResponse<NotebookLMProjectCheckResponse> checkProjects(@RequestBody NotebookLMProjectCheckRequest request) {
        List<String> names = request != null && request.getNames() != null ? request.getNames() : List.of();
        return ApiResponse.success(notebookLMService.checkProjectsByName(names));
    }

    @PostMapping("/projects")
    public ApiResponse<NotebookLMCreateProjectResponse> createProject(@RequestBody NotebookLMCreateProjectRequest request) {
        return ApiResponse.success("NotebookLM 프로젝트를 생성했습니다.", notebookLMService.createProject(request));
    }

    @GetMapping("/projects/history")
    public ApiResponse<NotebookLMProjectHistoryResponse> getProjectHistory(@RequestParam String projectName) {
        return ApiResponse.success(notebookLMService.getProjectHistory(projectName));
    }

    @GetMapping("/projects/steps")
    public ApiResponse<List<NotebookLMStepResponse>> getSteps() {
        return ApiResponse.success(notebookLMService.getSteps());
    }

    @PostMapping("/projects/steps")
    public ApiResponse<List<NotebookLMStepResponse>> saveSteps(@RequestBody NotebookLMSaveStepsRequest request) {
        return ApiResponse.success("프롬프트 단계를 저장했습니다.", notebookLMService.saveSteps(request));
    }

    @PostMapping("/projects/steps/run")
    public ApiResponse<NotebookLMStepRunStatusResponse> runStep(@RequestBody NotebookLMRunStepRequest request) {
        return ApiResponse.success("프롬프트 실행을 시작했습니다.", notebookLMService.startStepRun(request));
    }

    @GetMapping("/projects/steps/status")
    public ApiResponse<NotebookLMStepRunStatusResponse> getStepRunStatus(@RequestParam String projectName) {
        return ApiResponse.success(notebookLMService.getStepRunStatus(projectName));
    }

    @GetMapping("/projects/research-status")
    public ApiResponse<NotebookLMResearchStatusResponse> getResearchStatus(@RequestParam String projectName) {
        return ApiResponse.success(notebookLMService.getResearchStatus(projectName));
    }

    @GetMapping("/projects/sources")
    public ApiResponse<NotebookLMProjectSourcesResponse> getProjectSources(@RequestParam String projectName) {
        return ApiResponse.success(notebookLMService.getProjectSources(projectName));
    }

    @GetMapping("/projects/summary")
    public ApiResponse<NotebookLMProjectSummaryResponse> getProjectSummary(@RequestParam String projectName) {
        return ApiResponse.success(notebookLMService.getProjectSummary(projectName));
    }

    @GetMapping("/projects/sources/content")
    public ApiResponse<NotebookLMProjectSourceContentResponse> getProjectSourceContent(
            @RequestParam String projectName,
            @RequestParam String sourceId
    ) {
        return ApiResponse.success(notebookLMService.getProjectSourceContent(projectName, sourceId));
    }

    @PostMapping("/projects/sources/selection")
    public ApiResponse<NotebookLMProjectSourcesResponse> updateProjectSourceSelection(
            @RequestBody NotebookLMUpdateSourceSelectionRequest request
    ) {
        notebookLMService.updateProjectSourceSelection(request);
        String projectName = request != null ? request.getProjectName() : "";
        return ApiResponse.success(notebookLMService.getProjectSources(projectName));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<Void>> startLogin(@RequestBody NotebookLMLoginRequest request) {
        notebookLMService.startLogin(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("브라우저에서 Google 로그인을 완료해 주세요.", null));
    }

    @PostMapping(value = "/auth/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> importAuth(
            @RequestPart("masterToken") MultipartFile masterToken,
            @RequestPart("storageState") MultipartFile storageState
    ) {
        notebookLMService.importAuthFiles(masterToken, storageState);
        return ApiResponse.success("NotebookLM 인증 파일을 등록했습니다.", null);
    }
}
