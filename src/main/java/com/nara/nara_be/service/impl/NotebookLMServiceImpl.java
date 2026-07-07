package com.nara.nara_be.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nara.nara_be.config.NotebookLMProperties;
import com.nara.nara_be.dto.NotebookLMCreateProjectRequest;
import com.nara.nara_be.dto.NotebookLMCreateProjectResponse;
import com.nara.nara_be.dto.NotebookLMLoginRequest;
import com.nara.nara_be.dto.NotebookLMProjectCheckResponse;
import com.nara.nara_be.dto.NotebookLMProjectHistoryResponse;
import com.nara.nara_be.dto.NotebookLMResearchStatusResponse;
import com.nara.nara_be.dto.NotebookLMRunStepRequest;
import com.nara.nara_be.dto.NotebookLMRunStepResultResponse;
import com.nara.nara_be.dto.NotebookLMSaveStepsRequest;
import com.nara.nara_be.dto.NotebookLMStepItemRequest;
import com.nara.nara_be.dto.NotebookLMStepRunStatusResponse;
import com.nara.nara_be.dto.NotebookLMStepResponse;
import com.nara.nara_be.dto.NotebookLMProjectPromptResponse;
import com.nara.nara_be.dto.NotebookLMProjectSourceItemResponse;
import com.nara.nara_be.dto.NotebookLMProjectSourcesResponse;
import com.nara.nara_be.dto.NotebookLMProjectSourceContentResponse;
import com.nara.nara_be.dto.NotebookLMProjectSummaryResponse;
import com.nara.nara_be.dto.NotebookLMUpdateSourceSelectionRequest;
import com.nara.nara_be.dto.NotebookLMProjectSourceRequest;
import com.nara.nara_be.dto.NotebookLMStatusResponse;
import com.nara.nara_be.exception.BusinessException;
import com.nara.nara_be.service.GoogleDriveService;
import com.nara.nara_be.service.NotebookLMDeepResearchAsyncService;
import com.nara.nara_be.service.NotebookLMStepRunAsyncService;
import com.nara.nara_be.service.NotebookLMService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotebookLMServiceImpl implements NotebookLMService {

    private static final String DEFAULT_PROFILE = "default";

    private final NotebookLMProperties properties;
    private final ObjectMapper objectMapper;
    private final GoogleDriveService googleDriveService;

    @Autowired
    @Lazy
    private NotebookLMDeepResearchAsyncService deepResearchAsyncService;

    @Autowired
    @Lazy
    private NotebookLMStepRunAsyncService stepRunAsyncService;

    private final AtomicBoolean loginInProgress = new AtomicBoolean(false);
    private final AtomicReference<Process> loginProcess = new AtomicReference<>();
    private final Set<String> researchImportScheduled = ConcurrentHashMap.newKeySet();

    @Override
    public NotebookLMStatusResponse getStatus() {
        if (!properties.isEnabled()) {
            return baseStatusBuilder()
                    .message("NotebookLM 연동이 비활성화되어 있습니다.")
                    .build();
        }

        Path homePath = resolveHomePath();
        boolean pythonAvailable = resolvePythonExecutable() != null;
        boolean headlessReady = Files.isRegularFile(resolveStorageStatePath(homePath));

        if (!pythonAvailable) {
            return baseStatusBuilder()
                    .pythonAvailable(false)
                    .headlessReady(headlessReady)
                    .message("Python 또는 notebooklm-py가 설치되지 않았습니다. scripts/notebooklm/setup.ps1을 실행해 주세요.")
                    .build();
        }

        if (loginInProgress.get()) {
            return baseStatusBuilder()
                    .pythonAvailable(true)
                    .loginInProgress(true)
                    .headlessReady(headlessReady)
                    .accountEmail(readAccountEmail(homePath))
                    .message("구글 로그인 창에서 인증을 완료해 주세요.")
                    .build();
        }

        AuthCheckResult authCheck = runAuthCheck();
        return baseStatusBuilder()
                .pythonAvailable(true)
                .authenticated(authCheck.authenticated())
                .headlessReady(headlessReady)
                .accountEmail(authCheck.accountEmail())
                .message(authCheck.message())
                .build();
    }

    @Override
    public NotebookLMProjectCheckResponse checkProjectsByName(List<String> names) {
        Map<String, Boolean> matches = new LinkedHashMap<>();
        if (names != null) {
            for (String name : names) {
                if (StringUtils.hasText(name)) {
                    matches.put(name.trim(), false);
                }
            }
        }

        AuthCheckResult authCheck = runAuthCheck();
        if (!authCheck.authenticated()) {
            return NotebookLMProjectCheckResponse.builder()
                    .authenticated(false)
                    .matches(matches)
                    .build();
        }

        Set<String> notebookTitles = loadNotebookTitleKeys();
        for (String name : List.copyOf(matches.keySet())) {
            matches.put(name, notebookTitles.contains(normalizeProjectName(name)));
        }

        return NotebookLMProjectCheckResponse.builder()
                .authenticated(true)
                .matches(matches)
                .build();
    }

    @Override
    public NotebookLMCreateProjectResponse createProject(NotebookLMCreateProjectRequest request) {
        if (!properties.isEnabled()) {
            throw new BusinessException("NotebookLM 연동이 비활성화되어 있습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        String projectName = request != null && StringUtils.hasText(request.getProjectName())
                ? request.getProjectName().trim()
                : null;
        if (!StringUtils.hasText(projectName)) {
            throw new BusinessException("프로젝트 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        List<NotebookLMProjectSourceRequest> sources = request.getSources() != null
                ? request.getSources().stream()
                .filter(source -> source != null && StringUtils.hasText(source.getDriveFileId()))
                .toList()
                : List.of();
        if (sources.isEmpty()) {
            throw new BusinessException("소스로 추가할 파일을 하나 이상 선택해 주세요.", HttpStatus.BAD_REQUEST);
        }

        AuthCheckResult authCheck = runAuthCheck();
        if (!authCheck.authenticated()) {
            throw new BusinessException(
                    authCheck.message() != null ? authCheck.message() : "NotebookLM 계정이 연동되지 않았습니다.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            throw new BusinessException(
                    "Python 환경이 준비되지 않았습니다. scripts/notebooklm/setup.ps1을 먼저 실행해 주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        Path homePath = resolveHomePath();
        CliResult createResult = runCli(
                pythonExecutable,
                homePath,
                List.of("create", projectName, "--use", "--json"),
                properties.getCommandTimeoutSeconds()
        );
        if (createResult.exitCode() != 0) {
            throw new BusinessException(
                    parseMessage(createResult.output(), "NotebookLM 프로젝트 생성에 실패했습니다."),
                    HttpStatus.BAD_GATEWAY
            );
        }

        JsonNode createJson = parseJson(createResult.output());
        String notebookId = createJson != null ? createJson.path("notebook").path("id").asText(null) : null;
        if (!StringUtils.hasText(notebookId)) {
            notebookId = createJson != null ? createJson.path("active_notebook_id").asText(null) : null;
        }
        if (!StringUtils.hasText(notebookId)) {
            throw new BusinessException("NotebookLM 프로젝트 ID를 확인할 수 없습니다.", HttpStatus.BAD_GATEWAY);
        }

        int addedCount = 0;
        List<String> failedFiles = new ArrayList<>();
        for (NotebookLMProjectSourceRequest source : sources) {
            String fileName = StringUtils.hasText(source.getFileName()) ? source.getFileName().trim() : source.getDriveFileId();
            try {
                if (addDriveSource(pythonExecutable, homePath, notebookId, source)) {
                    addedCount += 1;
                } else {
                    failedFiles.add(fileName);
                }
            } catch (RuntimeException ex) {
                log.warn("NotebookLM source add failed for {}: {}", fileName, ex.getMessage());
                failedFiles.add(fileName);
            }
        }

        if (addedCount == 0) {
            throw new BusinessException(
                    "프로젝트는 생성되었지만 소스를 추가하지 못했습니다.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        List<String> initialSourceFileNames = new ArrayList<>();
        for (NotebookLMProjectSourceRequest source : sources) {
            String fileName = StringUtils.hasText(source.getFileName()) ? source.getFileName().trim() : source.getDriveFileId();
            if (!failedFiles.contains(fileName)) {
                initialSourceFileNames.add(fileName);
            }
        }
        saveProjectMetadata(notebookId, projectName, initialSourceFileNames);

        String deepResearchQuery = buildDeepResearchQuery(projectName);
        deepResearchAsyncService.start(notebookId, projectName);

        return NotebookLMCreateProjectResponse.builder()
                .notebookId(notebookId)
                .projectName(projectName)
                .addedSourceCount(addedCount)
                .failedFiles(failedFiles)
                .deepResearchStarted(true)
                .deepResearchQuery(deepResearchQuery)
                .build();
    }

    @Override
    public List<NotebookLMStepResponse> getSteps() {
        return loadStepDefinitions().stream()
                .map(this::toStepResponse)
                .toList();
    }

    @Override
    public List<NotebookLMStepResponse> saveSteps(NotebookLMSaveStepsRequest request) {
        List<StepDefinition> steps = normalizeStepDefinitions(
                request != null ? request.getSteps() : null
        );
        if (steps.isEmpty()) {
            throw new BusinessException("저장할 프롬프트 단계가 없습니다.", HttpStatus.BAD_REQUEST);
        }
        saveStepDefinitions(steps);
        return steps.stream().map(this::toStepResponse).toList();
    }

    @Override
    public NotebookLMStepRunStatusResponse startStepRun(NotebookLMRunStepRequest request) {
        ResolvedStepRunContext context = resolveStepRunContext(request);
        NotebookLMStepRunStatusResponse currentStatus = getStepRunStatus(context.projectName());
        if ("in_progress".equalsIgnoreCase(currentStatus.getStatus()) && !isStepRunTimedOut(loadStepRunStatus(context.notebookId()))) {
            log.info(
                    "NotebookLM STEP 재실행: projectName={}, previousStep={}, nextStep={}",
                    context.projectName(),
                    currentStatus.getStep(),
                    context.step()
            );
        }

        saveStepRunStatus(StepRunStatusFile.builder()
                .projectName(context.projectName())
                .notebookId(context.notebookId())
                .step(context.step())
                .status("in_progress")
                .message(context.step() + " 실행 중")
                .answer(null)
                .completedAt(null)
                .startedAt(Instant.now().toString())
                .build());

        stepRunAsyncService.run(context.projectName(), context.step());
        return getStepRunStatus(context.projectName());
    }

    @Override
    public NotebookLMStepRunStatusResponse getStepRunStatus(String projectName) {
        if (!StringUtils.hasText(projectName)) {
            throw new BusinessException("프로젝트 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String trimmedName = projectName.trim();
        String notebookId = findNotebookIdByProjectName(trimmedName);
        if (!StringUtils.hasText(notebookId)) {
            throw new BusinessException("해당 이름의 NotebookLM 프로젝트를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        StepRunStatusFile statusFile = loadStepRunStatus(notebookId);
        if (statusFile == null) {
            return buildIdleStepRunStatus(trimmedName, notebookId);
        }

        if ("in_progress".equalsIgnoreCase(statusFile.getStatus()) && isStepRunTimedOut(statusFile)) {
            statusFile.setStatus("failed");
            statusFile.setMessage("프롬프트 실행 시간이 초과되었습니다.");
            statusFile.setCompletedAt(Instant.now().toString());
            saveStepRunStatus(statusFile);
        }

        return toStepRunStatusResponse(statusFile);
    }

    @Override
    public void completeStepRun(String projectName, String step) {
        ResolvedStepRunContext context = resolveStepRunContext(
                createRunRequest(projectName, step)
        );
        try {
            NotebookLMRunStepResultResponse result = executeStepRun(context);
            saveStepRunStatus(StepRunStatusFile.builder()
                    .projectName(context.projectName())
                    .notebookId(context.notebookId())
                    .step(context.step())
                    .status("completed")
                    .message(context.step() + " 실행 완료")
                    .answer(result.getAnswer())
                    .startedAt(loadStepRunStartedAt(context.notebookId()))
                    .completedAt(Instant.now().toString())
                    .build());
        } catch (RuntimeException ex) {
            log.error("NotebookLM STEP 실행 실패: projectName={}, step={}", context.projectName(), context.step(), ex);
            saveStepRunStatus(StepRunStatusFile.builder()
                    .projectName(context.projectName())
                    .notebookId(context.notebookId())
                    .step(context.step())
                    .status("failed")
                    .message(ex.getMessage() != null ? ex.getMessage() : "프롬프트 실행에 실패했습니다.")
                    .startedAt(loadStepRunStartedAt(context.notebookId()))
                    .completedAt(Instant.now().toString())
                    .build());
        }
    }

    @Override
    public NotebookLMRunStepResultResponse runStep(NotebookLMRunStepRequest request) {
        ResolvedStepRunContext context = resolveStepRunContext(request);
        return executeStepRun(context);
    }

    private NotebookLMRunStepResultResponse executeStepRun(ResolvedStepRunContext context) {
        Path promptFile = null;
        try {
            String prompt = resolveStepPrompt(context.step(), context.projectName());
            promptFile = Files.createTempFile("notebooklm-step-", ".txt");
            Files.writeString(promptFile, prompt, StandardCharsets.UTF_8);

            List<String> askArgs = new ArrayList<>();
            askArgs.add("ask");
            askArgs.add("--prompt-file");
            askArgs.add(promptFile.toAbsolutePath().toString());
            askArgs.add("-n");
            askArgs.add(context.notebookId());
            appendSelectedSourceArgs(context.notebookId(), askArgs);
            askArgs.add("--json");
            askArgs.add("--timeout");
            askArgs.add(String.valueOf(properties.getAskTimeoutSeconds()));

            CliResult askResult = runCli(
                    context.pythonExecutable(),
                    resolveHomePath(),
                    askArgs,
                    properties.getAskTimeoutSeconds() + 30
            );

            if (askResult.exitCode() != 0) {
                throw new BusinessException(
                        parseMessage(askResult.output(), "NotebookLM 프롬프트 실행에 실패했습니다."),
                        HttpStatus.BAD_GATEWAY
                );
            }

            JsonNode askJson = parseJson(askResult.output());
            String answer = askJson != null ? askJson.path("answer").asText("") : "";
            if (!StringUtils.hasText(answer)) {
                throw new BusinessException("NotebookLM 응답을 확인할 수 없습니다.", HttpStatus.BAD_GATEWAY);
            }

            return NotebookLMRunStepResultResponse.builder()
                    .step(context.step())
                    .projectName(context.projectName())
                    .notebookId(context.notebookId())
                    .prompt(prompt)
                    .answer(answer)
                    .build();
        } catch (IOException ex) {
            throw new BusinessException("프롬프트 파일을 준비하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (promptFile != null) {
                try {
                    Files.deleteIfExists(promptFile);
                } catch (IOException ex) {
                    log.debug("Failed to delete prompt temp file {}", promptFile, ex);
                }
            }
        }
    }

    @Override
    public NotebookLMProjectSourcesResponse getProjectSources(String projectName) {
        if (!StringUtils.hasText(projectName)) {
            throw new BusinessException("프로젝트 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String trimmedName = projectName.trim();
        AuthCheckResult authCheck = runAuthCheck();
        if (!authCheck.authenticated()) {
            throw new BusinessException(
                    authCheck.message() != null ? authCheck.message() : "NotebookLM 계정이 연동되지 않았습니다.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        String notebookId = findNotebookIdByProjectName(trimmedName);
        if (!StringUtils.hasText(notebookId)) {
            throw new BusinessException("해당 이름의 NotebookLM 프로젝트를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        ProjectMetadataFile metadata = loadProjectMetadata(notebookId);
        if (metadata == null) {
            metadata = new ProjectMetadataFile();
            metadata.setNotebookId(notebookId);
            metadata.setProjectName(trimmedName);
        }

        if (metadata.getSelectedSourceIds() == null || metadata.getSelectedSourceIds().isEmpty()) {
            applyDefaultSourceSelection(notebookId, metadata);
            metadata = loadProjectMetadata(notebookId);
        } else if (shouldRefreshDefaultSourceSelection(notebookId, metadata)) {
            applyDefaultSourceSelection(notebookId, metadata);
            metadata = loadProjectMetadata(notebookId);
        }

        return buildProjectSourcesResponse(trimmedName, notebookId, metadata);
    }

    @Override
    public NotebookLMProjectSummaryResponse getProjectSummary(String projectName) {
        if (!StringUtils.hasText(projectName)) {
            throw new BusinessException("프로젝트 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String trimmedName = projectName.trim();
        AuthCheckResult authCheck = runAuthCheck();
        if (!authCheck.authenticated()) {
            return NotebookLMProjectSummaryResponse.builder()
                    .authenticated(false)
                    .projectName(trimmedName)
                    .showSummary(false)
                    .message("NotebookLM 미연동")
                    .build();
        }

        String notebookId = findNotebookIdByProjectName(trimmedName);
        if (!StringUtils.hasText(notebookId)) {
            throw new BusinessException("해당 이름의 NotebookLM 프로젝트를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        ProjectMetadataFile metadata = loadProjectMetadata(notebookId);
        String researchStatus = resolveResearchStatusValue(notebookId);
        if ("completed".equalsIgnoreCase(researchStatus)) {
            markDeepResearchCompleted(notebookId, metadata);
        }

        if (!shouldShowProjectSummary(notebookId, metadata, researchStatus)) {
            return NotebookLMProjectSummaryResponse.builder()
                    .authenticated(true)
                    .projectName(trimmedName)
                    .notebookId(notebookId)
                    .showSummary(false)
                    .message("Deep Research 완료 후에는 요약을 표시하지 않습니다.")
                    .build();
        }

        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            throw new BusinessException(
                    "Python 환경이 준비되지 않았습니다. scripts/notebooklm/setup.ps1을 먼저 실행해 주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        CliResult summaryResult = runCli(
                pythonExecutable,
                resolveHomePath(),
                List.of("summary", "-n", notebookId, "--json")
        );
        if (summaryResult.exitCode() != 0) {
            return NotebookLMProjectSummaryResponse.builder()
                    .authenticated(true)
                    .projectName(trimmedName)
                    .notebookId(notebookId)
                    .showSummary(true)
                    .message(parseMessage(summaryResult.output(), "NotebookLM 요약을 불러오지 못했습니다."))
                    .build();
        }

        JsonNode summaryJson = parseJson(summaryResult.output());
        String summary = summaryJson != null ? summaryJson.path("summary").asText("") : "";
        if (!StringUtils.hasText(summary)) {
            return NotebookLMProjectSummaryResponse.builder()
                    .authenticated(true)
                    .projectName(trimmedName)
                    .notebookId(notebookId)
                    .showSummary(true)
                    .message("첨부파일 요약을 생성하는 중입니다.")
                    .build();
        }

        return NotebookLMProjectSummaryResponse.builder()
                .authenticated(true)
                .projectName(trimmedName)
                .notebookId(notebookId)
                .showSummary(true)
                .summary(summary)
                .message("첨부파일 요약")
                .build();
    }

    @Override
    public NotebookLMProjectSourceContentResponse getProjectSourceContent(String projectName, String sourceId) {
        if (!StringUtils.hasText(projectName)) {
            throw new BusinessException("프로젝트 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(sourceId)) {
            throw new BusinessException("소스 ID가 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String trimmedName = projectName.trim();
        String trimmedSourceId = sourceId.trim();
        AuthCheckResult authCheck = runAuthCheck();
        if (!authCheck.authenticated()) {
            throw new BusinessException(
                    authCheck.message() != null ? authCheck.message() : "NotebookLM 계정이 연동되지 않았습니다.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        String notebookId = findNotebookIdByProjectName(trimmedName);
        if (!StringUtils.hasText(notebookId)) {
            throw new BusinessException("해당 이름의 NotebookLM 프로젝트를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            throw new BusinessException(
                    "Python 환경이 준비되지 않았습니다. scripts/notebooklm/setup.ps1을 먼저 실행해 주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        ParsedSource source = listNotebookSources(notebookId).stream()
                .filter(item -> trimmedSourceId.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("해당 소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("notebooklm-source-", ".md");
            CliResult fulltextResult = runCli(
                    pythonExecutable,
                    resolveHomePath(),
                    List.of(
                            "source", "fulltext",
                            trimmedSourceId,
                            "-n", notebookId,
                            "--format", "markdown",
                            "-o", tempFile.toAbsolutePath().toString(),
                            "--force"
                    ),
                    properties.getAskTimeoutSeconds()
            );
            if (fulltextResult.exitCode() != 0) {
                throw new BusinessException(
                        parseMessage(fulltextResult.output(), "소스 내용을 불러오지 못했습니다."),
                        HttpStatus.BAD_GATEWAY
                );
            }

            String content = Files.exists(tempFile) ? Files.readString(tempFile, StandardCharsets.UTF_8) : "";
            if (!StringUtils.hasText(content)) {
                throw new BusinessException("소스 내용이 비어 있습니다.", HttpStatus.BAD_GATEWAY);
            }

            return NotebookLMProjectSourceContentResponse.builder()
                    .projectName(trimmedName)
                    .notebookId(notebookId)
                    .sourceId(trimmedSourceId)
                    .title(source.title())
                    .content(content)
                    .build();
        } catch (IOException ex) {
            throw new BusinessException("소스 내용을 읽지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.debug("Failed to delete source temp file {}", tempFile, ex);
                }
            }
        }
    }

    @Override
    public void updateProjectSourceSelection(NotebookLMUpdateSourceSelectionRequest request) {
        if (request == null || !StringUtils.hasText(request.getProjectName())) {
            throw new BusinessException("프로젝트 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String projectName = request.getProjectName().trim();
        String notebookId = findNotebookIdByProjectName(projectName);
        if (!StringUtils.hasText(notebookId)) {
            throw new BusinessException("해당 이름의 NotebookLM 프로젝트를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        ProjectMetadataFile metadata = loadProjectMetadata(notebookId);
        if (metadata == null) {
            metadata = new ProjectMetadataFile();
            metadata.setNotebookId(notebookId);
            metadata.setProjectName(projectName);
            metadata.setInitialSourceFileNames(new ArrayList<>());
        }

        List<String> selectedSourceIds = request.getSelectedSourceIds() != null
                ? request.getSelectedSourceIds().stream().filter(StringUtils::hasText).map(String::trim).distinct().toList()
                : List.of();
        metadata.setSelectedSourceIds(new ArrayList<>(selectedSourceIds));
        saveProjectMetadata(metadata);
    }

    @Override
    public NotebookLMResearchStatusResponse getResearchStatus(String projectName) {
        if (!StringUtils.hasText(projectName)) {
            throw new BusinessException("프로젝트 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String trimmedName = projectName.trim();
        AuthCheckResult authCheck = runAuthCheck();
        if (!authCheck.authenticated()) {
            return NotebookLMResearchStatusResponse.builder()
                    .authenticated(false)
                    .projectName(trimmedName)
                    .status("unauthenticated")
                    .message("NotebookLM 미연동")
                    .build();
        }

        String notebookId = findNotebookIdByProjectName(trimmedName);
        if (!StringUtils.hasText(notebookId)) {
            throw new BusinessException("해당 이름의 NotebookLM 프로젝트를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            throw new BusinessException(
                    "Python 환경이 준비되지 않았습니다. scripts/notebooklm/setup.ps1을 먼저 실행해 주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        CliResult statusResult = runCli(
                pythonExecutable,
                resolveHomePath(),
                List.of("research", "status", "-n", notebookId, "--json")
        );

        if (statusResult.exitCode() != 0) {
            return NotebookLMResearchStatusResponse.builder()
                    .authenticated(true)
                    .projectName(trimmedName)
                    .notebookId(notebookId)
                    .status("unknown")
                    .message(parseMessage(statusResult.output(), "Deep Research 상태를 확인할 수 없습니다."))
                    .build();
        }

        JsonNode statusJson = parseJson(statusResult.output());
        String status = statusJson != null ? statusJson.path("status").asText("unknown") : "unknown";
        String query = statusJson != null ? firstNonBlank(statusJson.path("query").asText(null)) : null;
        int sourcesCount = 0;
        if (statusJson != null && statusJson.path("sources").isArray()) {
            sourcesCount = statusJson.path("sources").size();
        }

        if ("completed".equalsIgnoreCase(status) && sourcesCount > 0 && researchImportScheduled.add(notebookId)) {
            deepResearchAsyncService.importSources(notebookId, trimmedName);
        }
        if ("completed".equalsIgnoreCase(status)) {
            markDeepResearchCompleted(notebookId, loadProjectMetadata(notebookId));
        }

        return NotebookLMResearchStatusResponse.builder()
                .authenticated(true)
                .projectName(trimmedName)
                .notebookId(notebookId)
                .status(status)
                .query(query)
                .sourcesCount(sourcesCount)
                .message(resolveResearchStatusMessage(status))
                .build();
    }

    @Override
    public void importCompletedResearchSources(String notebookId) {
        if (!StringUtils.hasText(notebookId)) {
            throw new BusinessException("NotebookLM 프로젝트 ID가 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            throw new BusinessException(
                    "Python 환경이 준비되지 않았습니다. scripts/notebooklm/setup.ps1을 먼저 실행해 주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        log.info("NotebookLM Deep Research 소스 import 시작: notebookId={}", notebookId);
        CliResult waitResult = runCli(
                pythonExecutable,
                resolveHomePath(),
                List.of(
                        "research", "wait",
                        "-n", notebookId,
                        "--import-all",
                        "--json",
                        "--timeout", String.valueOf(properties.getResearchTimeoutSeconds()),
                        "--interval", "5"
                ),
                properties.getResearchTimeoutSeconds() + 60
        );

        if (waitResult.exitCode() != 0) {
            throw new BusinessException(
                    parseMessage(waitResult.output(), "Deep Research 소스 import에 실패했습니다."),
                    HttpStatus.BAD_GATEWAY
            );
        }

        JsonNode waitJson = parseJson(waitResult.output());
        int importedCount = waitJson != null ? waitJson.path("imported").asInt(0) : 0;
        log.info("NotebookLM Deep Research 소스 import 완료: notebookId={}, imported={}", notebookId, importedCount);

        ProjectMetadataFile metadata = loadProjectMetadata(notebookId);
        if (metadata != null) {
            applyDefaultSourceSelection(notebookId, metadata);
        }
        markDeepResearchCompleted(notebookId, loadProjectMetadata(notebookId));
    }

    @Override
    public void deleteProjectByName(String projectName) {
        if (!StringUtils.hasText(projectName) || !properties.isEnabled()) {
            return;
        }

        String trimmedName = projectName.trim();
        AuthCheckResult authCheck = runAuthCheck();
        if (!authCheck.authenticated()) {
            log.warn("NotebookLM 프로젝트 삭제 생략 (미연동): projectName={}", trimmedName);
            deleteProjectMetadataByProjectName(trimmedName);
            return;
        }

        String notebookId = findNotebookIdByProjectName(trimmedName);
        if (!StringUtils.hasText(notebookId)) {
            log.info("NotebookLM 프로젝트 없음, 삭제 생략: projectName={}", trimmedName);
            deleteProjectMetadataByProjectName(trimmedName);
            return;
        }

        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            log.warn("NotebookLM 프로젝트 삭제 생략 (Python 없음): projectName={}", trimmedName);
            return;
        }

        try {
            CliResult deleteResult = runCli(
                    pythonExecutable,
                    resolveHomePath(),
                    List.of("delete", "-n", notebookId, "-y", "--json")
            );
            if (deleteResult.exitCode() != 0) {
                log.warn(
                        "NotebookLM 프로젝트 삭제 실패: projectName={}, output={}",
                        trimmedName,
                        deleteResult.output()
                );
            } else {
                log.info("NotebookLM 프로젝트 삭제 완료: projectName={}, notebookId={}", trimmedName, notebookId);
            }
        } catch (RuntimeException ex) {
            log.warn("NotebookLM 프로젝트 삭제 실패: projectName={}", trimmedName, ex);
        } finally {
            researchImportScheduled.remove(notebookId);
            deleteProjectMetadata(notebookId);
            deleteStepRunStatus(notebookId);
        }
    }

    @Override
    public void runDeepResearch(String notebookId, String projectName) {
        if (!StringUtils.hasText(notebookId) || !StringUtils.hasText(projectName)) {
            throw new BusinessException("Deep Research 실행 정보가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            throw new BusinessException(
                    "Python 환경이 준비되지 않았습니다. scripts/notebooklm/setup.ps1을 먼저 실행해 주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        String query = buildDeepResearchQuery(projectName.trim());
        log.info("NotebookLM Deep Research 시작: notebookId={}, query={}", notebookId, query);

        CliResult startResult = runCli(
                pythonExecutable,
                resolveHomePath(),
                List.of(
                        "source", "add-research",
                        query,
                        "-n", notebookId,
                        "--from", "web",
                        "--mode", "deep",
                        "--no-wait",
                        "--json"
                ),
                properties.getCommandTimeoutSeconds()
        );
        if (startResult.exitCode() != 0) {
            throw new BusinessException(
                    parseMessage(startResult.output(), "NotebookLM Deep Research 시작에 실패했습니다."),
                    HttpStatus.BAD_GATEWAY
            );
        }

        importCompletedResearchSources(notebookId);
        log.info("NotebookLM Deep Research 완료: notebookId={}, projectName={}", notebookId, projectName);
    }

    @Override
    public NotebookLMProjectHistoryResponse getProjectHistory(String projectName) {
        if (!StringUtils.hasText(projectName)) {
            throw new BusinessException("프로젝트 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String trimmedName = projectName.trim();
        AuthCheckResult authCheck = runAuthCheck();
        if (!authCheck.authenticated()) {
            return NotebookLMProjectHistoryResponse.builder()
                    .authenticated(false)
                    .projectName(trimmedName)
                    .count(0)
                    .prompts(List.of())
                    .build();
        }

        String notebookId = findNotebookIdByProjectName(trimmedName);
        if (!StringUtils.hasText(notebookId)) {
            throw new BusinessException("해당 이름의 NotebookLM 프로젝트를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            throw new BusinessException(
                    "Python 환경이 준비되지 않았습니다. scripts/notebooklm/setup.ps1을 먼저 실행해 주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        CliResult historyResult = runCli(
                pythonExecutable,
                resolveHomePath(),
                List.of("history", "-n", notebookId, "--json", "--show-all"),
                properties.getCommandTimeoutSeconds()
        );
        if (historyResult.exitCode() != 0) {
            throw new BusinessException(
                    parseMessage(historyResult.output(), "NotebookLM 프롬프트 기록을 불러오지 못했습니다."),
                    HttpStatus.BAD_GATEWAY
            );
        }

        JsonNode historyJson = parseJson(historyResult.output());
        List<NotebookLMProjectPromptResponse> prompts = parsePromptHistory(historyJson);

        return NotebookLMProjectHistoryResponse.builder()
                .authenticated(true)
                .projectName(trimmedName)
                .notebookId(notebookId)
                .conversationId(historyJson != null ? historyJson.path("conversation_id").asText(null) : null)
                .count(prompts.size())
                .prompts(prompts)
                .build();
    }

    @Override
    public void startLogin(NotebookLMLoginRequest request) {
        if (!properties.isEnabled()) {
            throw new BusinessException("NotebookLM 연동이 비활성화되어 있습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            throw new BusinessException(
                    "Python 환경이 준비되지 않았습니다. scripts/notebooklm/setup.ps1을 먼저 실행해 주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        if (!loginInProgress.compareAndSet(false, true)) {
            throw new BusinessException("이미 NotebookLM 연동 로그인이 진행 중입니다.", HttpStatus.CONFLICT);
        }

        String accountEmail = request != null ? request.getAccountEmail() : null;

        Path homePath = resolveHomePath();
        try {
            Files.createDirectories(homePath);
        } catch (IOException ex) {
            loginInProgress.set(false);
            throw new BusinessException("NotebookLM 저장 경로를 만들 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Thread loginThread = new Thread(
                () -> runLoginProcess(pythonExecutable, homePath, StringUtils.hasText(accountEmail) ? accountEmail.trim() : null),
                "notebooklm-login"
        );
        loginThread.setDaemon(true);
        loginThread.start();
    }

    @Override
    public void importAuthFiles(MultipartFile masterToken, MultipartFile storageState) {
        if (storageState == null || storageState.isEmpty()) {
            throw new BusinessException("storage_state.json 파일이 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        Path homePath = resolveHomePath();
        Path profileDir = resolveProfileDir(homePath);
        try {
            Files.createDirectories(profileDir);
            storageState.transferTo(resolveStorageStatePath(homePath));
            if (masterToken != null && !masterToken.isEmpty()) {
                masterToken.transferTo(homePath.resolve("master_token.json"));
            }
        } catch (IOException ex) {
            throw new BusinessException("인증 파일을 저장하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        AuthCheckResult authCheck = runAuthCheck();
        if (!authCheck.authenticated()) {
            throw new BusinessException(
                    authCheck.message() != null ? authCheck.message() : "업로드한 인증 파일로 연동에 실패했습니다.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void runLoginProcess(String pythonExecutable, Path homePath, String accountEmail) {
        try {
            Files.createDirectories(resolveProfileDir(homePath));
            if (StringUtils.hasText(accountEmail)) {
                int cookieExitCode = runLoginCommand(
                        pythonExecutable,
                        homePath,
                        List.of("login", "--browser-cookies", "chrome", "--account", accountEmail)
                );
                if (cookieExitCode == 0) {
                    log.info("NotebookLM cookie login completed for {}", accountEmail);
                    return;
                }
                log.warn("NotebookLM cookie login failed for {}, retrying browser login", accountEmail);
            }

            int browserExitCode = runLoginCommand(
                    pythonExecutable,
                    homePath,
                    List.of("login", "--browser", "chrome")
            );
            if (browserExitCode != 0) {
                log.warn("NotebookLM browser login failed (exit {})", browserExitCode);
            } else {
                log.info("NotebookLM browser login completed");
            }
        } catch (IOException ex) {
            log.error("NotebookLM login process failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("NotebookLM login interrupted");
        } finally {
            loginProcess.set(null);
            loginInProgress.set(false);
        }
    }

    private int runLoginCommand(String pythonExecutable, Path homePath, List<String> args)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add("-m");
        command.add("notebooklm");
        command.addAll(args);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("NOTEBOOKLM_HOME", homePath.toAbsolutePath().toString());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        loginProcess.set(process);

        String output = readInputStream(process.getInputStream());
        boolean finished = process.waitFor(properties.getLoginTimeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("NotebookLM login timeout: {}", output);
            return -1;
        }
        if (process.exitValue() != 0) {
            log.warn("NotebookLM login failed (exit {}): {}", process.exitValue(), output);
        }
        return process.exitValue();
    }

    private AuthCheckResult runAuthCheck() {
        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            return new AuthCheckResult(false, null, "Python 환경이 준비되지 않았습니다.");
        }

        Path homePath = resolveHomePath();
        if (!Files.isDirectory(homePath)) {
            return new AuthCheckResult(false, null, "NotebookLM 계정이 연동되지 않았습니다.");
        }

        boolean hasAuthFile = Files.isRegularFile(resolveStorageStatePath(homePath))
                || Files.isRegularFile(homePath.resolve("master_token.json"));
        if (!hasAuthFile) {
            return new AuthCheckResult(false, null, "NotebookLM 계정이 연동되지 않았습니다.");
        }

        try {
            CliResult result = runCli(pythonExecutable, homePath, List.of("auth", "check", "--test", "--json"));
            if (result.exitCode() != 0) {
                return new AuthCheckResult(false, readAccountEmail(homePath), parseMessage(result.output(), "인증 확인에 실패했습니다."));
            }

            JsonNode json = parseJson(result.output());
            if (json == null) {
                return new AuthCheckResult(false, readAccountEmail(homePath), "인증 상태를 확인할 수 없습니다.");
            }

            String status = json.path("status").asText("");
            boolean authenticated = "ok".equalsIgnoreCase(status) || json.path("authenticated").asBoolean(false);
            String email = firstNonBlank(
                    json.path("email").asText(null),
                    json.path("account").asText(null),
                    readAccountEmail(homePath)
            );
            String message = authenticated
                    ? "NotebookLM 연동이 완료되었습니다."
                    : firstNonBlank(json.path("message").asText(null), json.path("error").asText(null), "NotebookLM 계정이 연동되지 않았습니다.");
            return new AuthCheckResult(authenticated, email, message);
        } catch (RuntimeException ex) {
            log.warn("NotebookLM auth check failed", ex);
            return new AuthCheckResult(false, readAccountEmail(homePath), "인증 확인 중 오류가 발생했습니다.");
        }
    }

    private CliResult runCli(String pythonExecutable, Path homePath, List<String> args) {
        return runCli(pythonExecutable, homePath, args, properties.getCommandTimeoutSeconds());
    }

    private CliResult runCli(String pythonExecutable, Path homePath, List<String> args, int timeoutSeconds) {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add("-m");
        command.add("notebooklm");
        command.addAll(args);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.environment().put("NOTEBOOKLM_HOME", homePath.toAbsolutePath().toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output = readInputStream(process.getInputStream());
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("NotebookLM 명령 실행 시간이 초과되었습니다.", HttpStatus.GATEWAY_TIMEOUT);
            }
            return new CliResult(process.exitValue(), output);
        } catch (IOException ex) {
            throw new BusinessException("NotebookLM 명령을 실행할 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("NotebookLM 명령 실행이 중단되었습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildDeepResearchQuery(String projectName) {
        return projectName + " 작업에 필요한 기술, 서비스, 경험, 용역 워크플로우 등 관련 정보를 찾아줘";
    }

    private String resolveStepPrompt(String step, String projectName) {
        String normalizedStep = step != null ? step.trim().toUpperCase(Locale.ROOT) : "";
        StepDefinition definition = loadStepDefinitions().stream()
                .filter(item -> normalizedStep.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("지원하지 않는 단계입니다: " + step, HttpStatus.BAD_REQUEST));

        String prompt = definition.prompt();
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException("프롬프트 내용이 비어 있습니다: " + step, HttpStatus.BAD_REQUEST);
        }
        return prompt.replace("{projectName}", projectName != null ? projectName : "");
    }

    private String buildStep1Prompt(String projectName) {
        return "제안요청서 목차대로 표지 1P, 섹션파트 4P, 주요내용본문 "
                + "(제안요청서에 명시된 제안서 제한 장수를 확인하여 적용하되, 관련 내용이 없으면 80페이지, 80페이지를 초과하면 80페이지로 제한) "
                + "총 (주요내용본문 장수 + 5페이지) 페이지 구성으로 "
                + "{projectName}"
                + " 제안서 내용을 마크다운 방식으로 텍스트로 작성해줘. 딥리서치와 요약서를 참조해서 상세하게 작성해줘";
    }

    private String buildStep2Prompt() {
        return "각 페이지 제목 아래에는 핵심 거버닝 메세지를 작성해줘. 전체 내용은 서술형 보다는 개조식 단문으로 작성. "
                + "너무 단순하게 함축하지 말고, 충분히 이해되도록 설명할 것. "
                + "각 페이지의 구성을 현재 2개로 일률적으로 구성된 것을 탈피하여 내용에 맞게 2~4개 항목으로 구성할 것. "
                + "마지막에는 클로징 페이지 작성할 것";
    }

    private List<StepDefinition> loadStepDefinitions() {
        Path stepsFile = resolveStepsFile();
        if (Files.isRegularFile(stepsFile)) {
            try {
                StepsFile stepsConfig = objectMapper.readValue(stepsFile.toFile(), StepsFile.class);
                List<StepDefinition> steps = normalizeStepDefinitionsFromStored(
                        stepsConfig != null ? stepsConfig.getSteps() : null
                );
                if (!steps.isEmpty()) {
                    return steps;
                }
            } catch (Exception ex) {
                log.warn("NotebookLM steps load failed, using defaults", ex);
            }
        }
        return defaultStepDefinitions();
    }

    private void saveStepDefinitions(List<StepDefinition> steps) {
        try {
            Path stepsFile = resolveStepsFile();
            Files.createDirectories(stepsFile.getParent());
            StepsFile stepsConfig = new StepsFile();
            stepsConfig.setSteps(steps.stream().map(this::toStoredStep).toList());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stepsFile.toFile(), stepsConfig);
        } catch (IOException ex) {
            throw new BusinessException("프롬프트 단계를 저장하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<StepDefinition> defaultStepDefinitions() {
        return List.of(
                new StepDefinition("STEP1", "STEP1", buildStep1Prompt("{projectName}")),
                new StepDefinition("STEP2", "STEP2", buildStep2Prompt())
        );
    }

    private List<StepDefinition> normalizeStepDefinitions(List<NotebookLMStepItemRequest> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }

        List<StepDefinition> normalized = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        for (NotebookLMStepItemRequest step : steps) {
            if (step == null) {
                continue;
            }
            String id = step.getId() != null ? step.getId().trim().toUpperCase(Locale.ROOT) : "";
            String label = StringUtils.hasText(step.getLabel()) ? step.getLabel().trim() : id;
            String prompt = step.getPrompt() != null ? step.getPrompt().trim() : "";
            if (!StringUtils.hasText(id) || !StringUtils.hasText(label) || !StringUtils.hasText(prompt)) {
                continue;
            }
            if (!usedIds.add(id)) {
                throw new BusinessException("중복된 단계 ID가 있습니다: " + id, HttpStatus.BAD_REQUEST);
            }
            normalized.add(new StepDefinition(id, label, prompt));
        }
        return normalized;
    }

    private List<StepDefinition> normalizeStepDefinitionsFromStored(List<StoredStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }

        List<NotebookLMStepItemRequest> requests = new ArrayList<>();
        for (StoredStep step : steps) {
            if (step == null) {
                continue;
            }
            NotebookLMStepItemRequest request = new NotebookLMStepItemRequest();
            request.setId(step.getId());
            request.setLabel(step.getLabel());
            request.setPrompt(step.getPrompt());
            requests.add(request);
        }
        return normalizeStepDefinitions(requests);
    }

    private NotebookLMStepResponse toStepResponse(StepDefinition step) {
        return NotebookLMStepResponse.builder()
                .id(step.id())
                .label(step.label())
                .prompt(step.prompt())
                .build();
    }

    private StoredStep toStoredStep(StepDefinition step) {
        StoredStep storedStep = new StoredStep();
        storedStep.setId(step.id());
        storedStep.setLabel(step.label());
        storedStep.setPrompt(step.prompt());
        return storedStep;
    }

    private Path resolveStepsFile() {
        return resolveHomePath().resolve("steps.json");
    }

    private boolean shouldShowProjectSummary(String notebookId, ProjectMetadataFile metadata, String researchStatus) {
        if (metadata != null && metadata.isDeepResearchCompleted()) {
            return false;
        }
        if ("completed".equalsIgnoreCase(researchStatus)) {
            return false;
        }
        if ("no_research".equalsIgnoreCase(researchStatus)) {
            ProjectMetadataFile resolvedMetadata = metadata != null ? metadata : loadProjectMetadata(notebookId);
            Set<String> initialTitles = normalizeSourceTitles(
                    resolvedMetadata != null ? resolvedMetadata.getInitialSourceFileNames() : List.of()
            );
            for (ParsedSource parsedSource : listNotebookSources(notebookId)) {
                if (isResearchReportMarkdown(parsedSource, initialTitles)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String resolveResearchStatusValue(String notebookId) {
        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            return "unknown";
        }

        CliResult statusResult = runCli(
                pythonExecutable,
                resolveHomePath(),
                List.of("research", "status", "-n", notebookId, "--json")
        );
        if (statusResult.exitCode() != 0) {
            return "unknown";
        }

        JsonNode statusJson = parseJson(statusResult.output());
        return statusJson != null ? statusJson.path("status").asText("unknown") : "unknown";
    }

    private void markDeepResearchCompleted(String notebookId, ProjectMetadataFile metadata) {
        if (!StringUtils.hasText(notebookId)) {
            return;
        }
        ProjectMetadataFile resolvedMetadata = metadata;
        if (resolvedMetadata == null) {
            resolvedMetadata = loadProjectMetadata(notebookId);
        }
        if (resolvedMetadata == null) {
            resolvedMetadata = new ProjectMetadataFile();
            resolvedMetadata.setNotebookId(notebookId);
        }
        if (resolvedMetadata.isDeepResearchCompleted()) {
            return;
        }
        resolvedMetadata.setDeepResearchCompleted(true);
        saveProjectMetadata(resolvedMetadata);
    }

    private String resolveResearchStatusMessage(String status) {
        if (!StringUtils.hasText(status)) {
            return "상태 확인 불가";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "in_progress" -> "Deep Research 진행 중";
            case "completed" -> "Deep Research 완료";
            case "no_research" -> "Deep Research 없음";
            case "failed" -> "Deep Research 실패";
            case "timeout" -> "Deep Research 시간 초과";
            default -> "Deep Research 상태: " + status;
        };
    }

    private String normalizeProjectName(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replace("/", "-")
                .replace("\\", "-")
                .replaceAll("[\\x00-\\x1f]", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private boolean addDriveSource(
            String pythonExecutable,
            Path homePath,
            String notebookId,
            NotebookLMProjectSourceRequest source
    ) {
        String driveMimeType = resolveDriveMimeType(source.getMimeType());
        if (driveMimeType != null) {
            CliResult result = runCli(
                    pythonExecutable,
                    homePath,
                    List.of(
                            "source", "add-drive",
                            "-n", notebookId,
                            source.getDriveFileId().trim(),
                            resolveSourceTitle(source),
                            "--mime-type", driveMimeType,
                            "--json"
                    ),
                    properties.getCommandTimeoutSeconds()
            );
            return result.exitCode() == 0;
        }

        return addDownloadedFileSource(pythonExecutable, homePath, notebookId, source);
    }

    private boolean addDownloadedFileSource(
            String pythonExecutable,
            Path homePath,
            String notebookId,
            NotebookLMProjectSourceRequest source
    ) {
        Path tempFile = null;
        try {
            Resource resource = googleDriveService.downloadFile(source.getDriveFileId().trim());
            String fileName = resolveSourceTitle(source);
            String suffix = extractFileSuffix(fileName);
            tempFile = Files.createTempFile("notebooklm-source-", suffix);
            Files.write(tempFile, resource.getContentAsByteArray());

            CliResult result = runCli(
                    pythonExecutable,
                    homePath,
                    List.of(
                            "source", "add",
                            "-n", notebookId,
                            tempFile.toAbsolutePath().toString(),
                            "--type", "file",
                            "--title", fileName,
                            "--json"
                    ),
                    properties.getCommandTimeoutSeconds()
            );
            return result.exitCode() == 0;
        } catch (IOException ex) {
            throw new BusinessException("Google Drive 파일을 다운로드하지 못했습니다.", HttpStatus.BAD_GATEWAY);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.debug("Failed to delete temp file {}", tempFile, ex);
                }
            }
        }
    }

    private String resolveDriveMimeType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return null;
        }
        return switch (mimeType.trim().toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> "pdf";
            case "application/vnd.google-apps.document" -> "google-doc";
            case "application/vnd.google-apps.spreadsheet" -> "google-sheets";
            case "application/vnd.google-apps.presentation" -> "google-slides";
            default -> null;
        };
    }

    private String resolveSourceTitle(NotebookLMProjectSourceRequest source) {
        if (StringUtils.hasText(source.getFileName())) {
            return source.getFileName().trim();
        }
        return source.getDriveFileId().trim();
    }

    private String extractFileSuffix(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String extension = fileName.substring(dotIndex);
            if (extension.length() <= 12) {
                return extension;
            }
        }
        return ".bin";
    }

    private void appendSelectedSourceArgs(String notebookId, List<String> askArgs) {
        ProjectMetadataFile metadata = loadProjectMetadata(notebookId);
        if (metadata == null || metadata.getSelectedSourceIds() == null) {
            return;
        }
        for (String sourceId : metadata.getSelectedSourceIds()) {
            if (StringUtils.hasText(sourceId)) {
                askArgs.add("-s");
                askArgs.add(sourceId.trim());
            }
        }
    }

    private NotebookLMProjectSourcesResponse buildProjectSourcesResponse(
            String projectName,
            String notebookId,
            ProjectMetadataFile metadata
    ) {
        List<ParsedSource> parsedSources = listNotebookSources(notebookId);
        Set<String> selectedIds = new HashSet<>();
        if (metadata.getSelectedSourceIds() != null) {
            metadata.getSelectedSourceIds().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(selectedIds::add);
        }

        Set<String> initialTitles = normalizeSourceTitles(metadata.getInitialSourceFileNames());
        List<ParsedSource> selectionSources = filterSelectionSources(parsedSources, initialTitles);
        List<NotebookLMProjectSourceItemResponse> sources = new ArrayList<>();
        for (ParsedSource parsedSource : selectionSources) {
            String category = resolveSourceCategory(parsedSource, initialTitles);
            sources.add(NotebookLMProjectSourceItemResponse.builder()
                    .id(parsedSource.id())
                    .title(parsedSource.title())
                    .type(parsedSource.type())
                    .checked(selectedIds.contains(parsedSource.id()))
                    .category(category)
                    .build());
        }

        return NotebookLMProjectSourcesResponse.builder()
                .projectName(projectName)
                .notebookId(notebookId)
                .sources(sources)
                .build();
    }

    private void applyDefaultSourceSelection(String notebookId, ProjectMetadataFile metadata) {
        List<ParsedSource> parsedSources = listNotebookSources(notebookId);
        Set<String> initialTitles = normalizeSourceTitles(metadata.getInitialSourceFileNames());

        LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
        for (ParsedSource parsedSource : parsedSources) {
            if (initialTitles.contains(normalizeSourceTitle(parsedSource.title()))) {
                selectedIds.add(parsedSource.id());
            }
        }

        for (ParsedSource parsedSource : parsedSources) {
            if (isResearchReportMarkdown(parsedSource, initialTitles)) {
                selectedIds.add(parsedSource.id());
                break;
            }
        }

        metadata.setSelectedSourceIds(new ArrayList<>(selectedIds));
        saveProjectMetadata(metadata);
        log.info("NotebookLM 기본 소스 선택 적용: notebookId={}, selected={}", notebookId, selectedIds.size());
    }

    private boolean shouldRefreshDefaultSourceSelection(String notebookId, ProjectMetadataFile metadata) {
        if (metadata == null) {
            return false;
        }
        List<ParsedSource> parsedSources = listNotebookSources(notebookId);
        Set<String> initialTitles = normalizeSourceTitles(metadata.getInitialSourceFileNames());
        ParsedSource researchReport = findResearchReportSource(parsedSources, initialTitles);
        if (researchReport == null) {
            return false;
        }

        Set<String> selectedIds = metadata.getSelectedSourceIds() != null
                ? metadata.getSelectedSourceIds().stream().filter(StringUtils::hasText).map(String::trim).collect(Collectors.toSet())
                : Set.of();
        return !selectedIds.contains(researchReport.id());
    }

    private List<ParsedSource> filterSelectionSources(List<ParsedSource> parsedSources, Set<String> initialTitles) {
        List<ParsedSource> filtered = new ArrayList<>();
        for (ParsedSource parsedSource : parsedSources) {
            if (initialTitles.contains(normalizeSourceTitle(parsedSource.title()))) {
                filtered.add(parsedSource);
            }
        }

        ParsedSource researchReport = findResearchReportSource(parsedSources, initialTitles);
        if (researchReport != null) {
            filtered.add(researchReport);
        }
        return filtered;
    }

    private ParsedSource findResearchReportSource(List<ParsedSource> parsedSources, Set<String> initialTitles) {
        for (ParsedSource parsedSource : parsedSources) {
            if (isResearchReportMarkdown(parsedSource, initialTitles)) {
                return parsedSource;
            }
        }
        return null;
    }

    private boolean isResearchReportMarkdown(ParsedSource source, Set<String> initialTitles) {
        String title = source.title() != null ? source.title().trim() : "";
        if (!StringUtils.hasText(title)) {
            return false;
        }
        if (initialTitles.contains(normalizeSourceTitle(title))) {
            return false;
        }

        String lowerTitle = title.toLowerCase(Locale.ROOT);
        if (lowerTitle.contains("deep research")
                || lowerTitle.contains("deep research 보고서")
                || lowerTitle.contains("종합 연구 보고서")
                || lowerTitle.contains("연구 보고서")
                || (lowerTitle.contains("리서치") && lowerTitle.contains("보고서"))
                || lowerTitle.contains("research report")) {
            return true;
        }

        boolean markdownType = "markdown".equalsIgnoreCase(source.type());
        boolean markdownName = lowerTitle.endsWith(".md");
        if (!markdownType && !markdownName) {
            return false;
        }

        return lowerTitle.contains("research")
                || lowerTitle.contains("리서치")
                || lowerTitle.contains("보고서")
                || lowerTitle.contains("report")
                || markdownName;
    }

    private String resolveSourceCategory(ParsedSource source, Set<String> initialTitles) {
        if (initialTitles.contains(normalizeSourceTitle(source.title()))) {
            return "initial";
        }
        if (isResearchReportMarkdown(source, initialTitles)) {
            return "research_report";
        }
        return "other";
    }

    private List<ParsedSource> listNotebookSources(String notebookId) {
        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            return List.of();
        }

        try {
            CliResult result = runCli(
                    pythonExecutable,
                    resolveHomePath(),
                    List.of("source", "list", "-n", notebookId, "--json")
            );
            if (result.exitCode() != 0) {
                log.warn("NotebookLM source list failed: {}", result.output());
                return List.of();
            }

            JsonNode json = parseJson(result.output());
            if (json == null) {
                return List.of();
            }

            JsonNode sources = json.path("sources");
            if (!sources.isArray()) {
                return List.of();
            }

            List<ParsedSource> parsedSources = new ArrayList<>();
            for (JsonNode sourceNode : sources) {
                String id = sourceNode.path("id").asText(null);
                if (!StringUtils.hasText(id)) {
                    continue;
                }
                parsedSources.add(new ParsedSource(
                        id,
                        sourceNode.path("title").asText(""),
                        sourceNode.path("type").asText("")
                ));
            }
            return parsedSources;
        } catch (RuntimeException ex) {
            log.warn("NotebookLM source list failed", ex);
            return List.of();
        }
    }

    private void saveProjectMetadata(String notebookId, String projectName, List<String> initialSourceFileNames) {
        ProjectMetadataFile metadata = new ProjectMetadataFile();
        metadata.setNotebookId(notebookId);
        metadata.setProjectName(projectName);
        metadata.setInitialSourceFileNames(new ArrayList<>(initialSourceFileNames));
        metadata.setSelectedSourceIds(new ArrayList<>());
        saveProjectMetadata(metadata);
    }

    private void saveProjectMetadata(ProjectMetadataFile metadata) {
        if (metadata == null || !StringUtils.hasText(metadata.getNotebookId())) {
            return;
        }
        try {
            Path metadataDir = resolveProjectMetadataDir();
            Files.createDirectories(metadataDir);
            Path metadataFile = metadataDir.resolve(metadata.getNotebookId() + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataFile.toFile(), metadata);
        } catch (IOException ex) {
            log.warn("NotebookLM project metadata save failed for {}", metadata.getNotebookId(), ex);
        }
    }

    private ProjectMetadataFile loadProjectMetadata(String notebookId) {
        if (!StringUtils.hasText(notebookId)) {
            return null;
        }
        Path metadataFile = resolveProjectMetadataDir().resolve(notebookId + ".json");
        if (!Files.isRegularFile(metadataFile)) {
            return null;
        }
        try {
            return objectMapper.readValue(metadataFile.toFile(), ProjectMetadataFile.class);
        } catch (Exception ex) {
            log.warn("NotebookLM project metadata load failed for {}", notebookId, ex);
        }
        return null;
    }

    private void deleteProjectMetadata(String notebookId) {
        if (!StringUtils.hasText(notebookId)) {
            return;
        }
        Path metadataFile = resolveProjectMetadataDir().resolve(notebookId + ".json");
        try {
            Files.deleteIfExists(metadataFile);
        } catch (IOException ex) {
            log.warn("NotebookLM project metadata delete failed for {}", notebookId, ex);
        }
    }

    private void deleteProjectMetadataByProjectName(String projectName) {
        if (!StringUtils.hasText(projectName)) {
            return;
        }
        Path metadataDir = resolveProjectMetadataDir();
        if (!Files.isDirectory(metadataDir)) {
            return;
        }

        String normalizedName = normalizeProjectName(projectName);
        try (var stream = Files.list(metadataDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            ProjectMetadataFile metadata = objectMapper.readValue(path.toFile(), ProjectMetadataFile.class);
                            if (metadata != null
                                    && normalizedName.equals(normalizeProjectName(metadata.getProjectName()))) {
                                Files.deleteIfExists(path);
                            }
                        } catch (Exception ex) {
                            log.debug("NotebookLM metadata scan failed for {}", path, ex);
                        }
                    });
        } catch (IOException ex) {
            log.warn("NotebookLM project metadata cleanup failed for {}", projectName, ex);
        }
    }

    private Path resolveProjectMetadataDir() {
        return resolveHomePath().resolve("project-metadata");
    }

    private Set<String> normalizeSourceTitles(List<String> titles) {
        if (titles == null || titles.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String title : titles) {
            String normalizedTitle = normalizeSourceTitle(title);
            if (StringUtils.hasText(normalizedTitle)) {
                normalized.add(normalizedTitle);
            }
        }
        return normalized;
    }

    private String normalizeSourceTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        return title.trim().toLowerCase(Locale.ROOT);
    }

    private NotebookLMStatusResponse.NotebookLMStatusResponseBuilder baseStatusBuilder() {
        return NotebookLMStatusResponse.builder()
                .enabled(properties.isEnabled())
                .homePath(resolveHomePath().toAbsolutePath().toString().replace('\\', '/'))
                .loginInProgress(loginInProgress.get());
    }

    private Path resolveHomePath() {
        return Path.of(properties.getHomePath().trim()).toAbsolutePath().normalize();
    }

    private String resolvePythonExecutable() {
        if (StringUtils.hasText(properties.getPythonExecutable())) {
            Path configured = Path.of(properties.getPythonExecutable().trim());
            if (Files.isRegularFile(configured)) {
                return configured.toAbsolutePath().toString();
            }
        }

        Path windowsVenv = Path.of("scripts/notebooklm/.venv/Scripts/python.exe");
        if (Files.isRegularFile(windowsVenv)) {
            return windowsVenv.toAbsolutePath().toString();
        }

        Path unixVenv = Path.of("scripts/notebooklm/.venv/bin/python");
        if (Files.isRegularFile(unixVenv)) {
            return unixVenv.toAbsolutePath().toString();
        }

        return null;
    }

    private Path resolveProfileDir(Path homePath) {
        return homePath.resolve("profiles").resolve(DEFAULT_PROFILE);
    }

    private Path resolveStorageStatePath(Path homePath) {
        return resolveProfileDir(homePath).resolve("storage_state.json");
    }

    private Set<String> loadNotebookTitleKeys() {
        return loadNotebookEntries().keySet();
    }

    private Map<String, String> loadNotebookEntries() {
        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            return Map.of();
        }

        try {
            CliResult result = runCli(pythonExecutable, resolveHomePath(), List.of("list", "--json"));
            if (result.exitCode() != 0) {
                log.warn("NotebookLM list failed: {}", result.output());
                return Map.of();
            }

            JsonNode json = parseJson(result.output());
            if (json == null) {
                return Map.of();
            }

            JsonNode notebooks = json.path("notebooks");
            if (!notebooks.isArray()) {
                return Map.of();
            }

            Map<String, String> entries = new LinkedHashMap<>();
            for (JsonNode notebook : notebooks) {
                String title = notebook.path("title").asText(null);
                String id = notebook.path("id").asText(null);
                String normalized = normalizeProjectName(title);
                if (StringUtils.hasText(normalized) && StringUtils.hasText(id)) {
                    entries.putIfAbsent(normalized, id);
                }
            }
            return entries;
        } catch (RuntimeException ex) {
            log.warn("NotebookLM notebook list failed", ex);
            return Map.of();
        }
    }

    private String findNotebookIdByProjectName(String projectName) {
        return loadNotebookEntries().get(normalizeProjectName(projectName));
    }

    private List<NotebookLMProjectPromptResponse> parsePromptHistory(JsonNode historyJson) {
        if (historyJson == null) {
            return List.of();
        }

        JsonNode qaPairs = historyJson.path("qa_pairs");
        if (!qaPairs.isArray()) {
            return List.of();
        }

        List<NotebookLMProjectPromptResponse> prompts = new ArrayList<>();
        for (JsonNode qaPair : qaPairs) {
            String question = qaPair.path("question").asText("");
            String answer = qaPair.path("answer").asText("");
            int turn = qaPair.path("turn").asInt(prompts.size() + 1);
            prompts.add(NotebookLMProjectPromptResponse.builder()
                    .turn(turn)
                    .question(question)
                    .answer(answer)
                    .build());
        }
        return prompts;
    }

    private String readAccountEmail(Path homePath) {
        Path masterToken = homePath.resolve("master_token.json");
        if (!Files.isRegularFile(masterToken)) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(Files.readString(masterToken, StandardCharsets.UTF_8));
            return firstNonBlank(json.path("email").asText(null), json.path("account").asText(null));
        } catch (IOException ex) {
            return null;
        }
    }

    private JsonNode parseJson(String output) {
        if (!StringUtils.hasText(output)) {
            return null;
        }
        String trimmed = output.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(trimmed.substring(start, end + 1));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String parseMessage(String output, String fallback) {
        JsonNode json = parseJson(output);
        if (json == null) {
            return fallback;
        }
        return firstNonBlank(json.path("message").asText(null), json.path("error").asText(null), fallback);
    }

    private String readInputStream(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private ResolvedStepRunContext resolveStepRunContext(NotebookLMRunStepRequest request) {
        if (!properties.isEnabled()) {
            throw new BusinessException("NotebookLM 연동이 비활성화되어 있습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        String projectName = request != null && StringUtils.hasText(request.getProjectName())
                ? request.getProjectName().trim()
                : null;
        String step = request != null && StringUtils.hasText(request.getStep())
                ? request.getStep().trim().toUpperCase(Locale.ROOT)
                : null;
        if (!StringUtils.hasText(projectName)) {
            throw new BusinessException("프로젝트 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(step)) {
            throw new BusinessException("실행할 단계가 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        AuthCheckResult authCheck = runAuthCheck();
        if (!authCheck.authenticated()) {
            throw new BusinessException(
                    authCheck.message() != null ? authCheck.message() : "NotebookLM 계정이 연동되지 않았습니다.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        String notebookId = findNotebookIdByProjectName(projectName);
        if (!StringUtils.hasText(notebookId)) {
            throw new BusinessException("해당 이름의 NotebookLM 프로젝트를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        String pythonExecutable = resolvePythonExecutable();
        if (pythonExecutable == null) {
            throw new BusinessException(
                    "Python 환경이 준비되지 않았습니다. scripts/notebooklm/setup.ps1을 먼저 실행해 주세요.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        return new ResolvedStepRunContext(projectName, step, notebookId, pythonExecutable);
    }

    private NotebookLMRunStepRequest createRunRequest(String projectName, String step) {
        NotebookLMRunStepRequest request = new NotebookLMRunStepRequest();
        request.setProjectName(projectName);
        request.setStep(step);
        return request;
    }

    private NotebookLMStepRunStatusResponse buildIdleStepRunStatus(String projectName, String notebookId) {
        return NotebookLMStepRunStatusResponse.builder()
                .projectName(projectName)
                .notebookId(notebookId)
                .status("idle")
                .message("실행 중인 프롬프트가 없습니다.")
                .build();
    }

    private NotebookLMStepRunStatusResponse toStepRunStatusResponse(StepRunStatusFile statusFile) {
        return NotebookLMStepRunStatusResponse.builder()
                .projectName(statusFile.getProjectName())
                .notebookId(statusFile.getNotebookId())
                .step(statusFile.getStep())
                .status(statusFile.getStatus())
                .message(statusFile.getMessage())
                .answer(statusFile.getAnswer())
                .build();
    }

    private void saveStepRunStatus(StepRunStatusFile statusFile) {
        if (statusFile == null || !StringUtils.hasText(statusFile.getNotebookId())) {
            return;
        }

        Path statusDir = resolveStepRunStatusDir();
        try {
            Files.createDirectories(statusDir);
            Path statusFilePath = statusDir.resolve(statusFile.getNotebookId() + ".json");
            objectMapper.writeValue(statusFilePath.toFile(), statusFile);
        } catch (IOException ex) {
            log.warn("NotebookLM step run status save failed for {}", statusFile.getNotebookId(), ex);
        }
    }

    private StepRunStatusFile loadStepRunStatus(String notebookId) {
        if (!StringUtils.hasText(notebookId)) {
            return null;
        }

        Path statusFile = resolveStepRunStatusDir().resolve(notebookId + ".json");
        if (!Files.isRegularFile(statusFile)) {
            return null;
        }

        try {
            return objectMapper.readValue(statusFile.toFile(), StepRunStatusFile.class);
        } catch (Exception ex) {
            log.warn("NotebookLM step run status load failed for {}", notebookId, ex);
        }
        return null;
    }

    private void deleteStepRunStatus(String notebookId) {
        if (!StringUtils.hasText(notebookId)) {
            return;
        }

        Path statusFile = resolveStepRunStatusDir().resolve(notebookId + ".json");
        try {
            Files.deleteIfExists(statusFile);
        } catch (IOException ex) {
            log.warn("NotebookLM step run status delete failed for {}", notebookId, ex);
        }
    }

    private String loadStepRunStartedAt(String notebookId) {
        StepRunStatusFile statusFile = loadStepRunStatus(notebookId);
        return statusFile != null ? statusFile.getStartedAt() : Instant.now().toString();
    }

    private boolean isStepRunTimedOut(StepRunStatusFile statusFile) {
        if (statusFile == null || !StringUtils.hasText(statusFile.getStartedAt())) {
            return false;
        }

        try {
            Instant startedAt = Instant.parse(statusFile.getStartedAt());
            long elapsedSeconds = Instant.now().getEpochSecond() - startedAt.getEpochSecond();
            return elapsedSeconds > properties.getAskTimeoutSeconds() + 60L;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Path resolveStepRunStatusDir() {
        return resolveHomePath().resolve("step-runs");
    }

    private record ResolvedStepRunContext(
            String projectName,
            String step,
            String notebookId,
            String pythonExecutable
    ) {
    }

    private record AuthCheckResult(boolean authenticated, String accountEmail, String message) {
    }

    private record CliResult(int exitCode, String output) {
    }

    private record ParsedSource(String id, String title, String type) {
    }

    private record StepDefinition(String id, String label, String prompt) {
    }

    @Getter
    @Setter
    private static class StepsFile {
        private List<StoredStep> steps = new ArrayList<>();
    }

    @Getter
    @Setter
    private static class StoredStep {
        private String id;
        private String label;
        private String prompt;
    }

    @Getter
    @Setter
    @Builder
    private static class StepRunStatusFile {
        private String projectName;
        private String notebookId;
        private String step;
        private String status;
        private String message;
        private String answer;
        private String startedAt;
        private String completedAt;
    }

    @Getter
    @Setter
    private static class ProjectMetadataFile {
        private String notebookId;
        private String projectName;
        private List<String> initialSourceFileNames = new ArrayList<>();
        private List<String> selectedSourceIds = new ArrayList<>();
        private boolean deepResearchCompleted;
    }
}
