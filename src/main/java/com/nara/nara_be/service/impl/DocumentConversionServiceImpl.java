package com.nara.nara_be.service.impl;

import com.nara.nara_be.config.StorageProperties;
import com.nara.nara_be.service.DocumentConversionService;
import com.nara.nara_be.service.support.LibreOfficeSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentConversionServiceImpl implements DocumentConversionService {

    private static final String HWP_INFILTER = "Hwp2002_File";
    private static final String PDF_EXPORT_FILTER = "pdf:writer_pdf_Export";

    private final StorageProperties storageProperties;

    @Override
    public Path convertHancomDocumentToPdf(Path sourceFile) {
        if (sourceFile == null || !Files.exists(sourceFile)) {
            throw new IllegalArgumentException("변환할 파일이 없습니다.");
        }

        String fileName = sourceFile.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".hwpx") && !fileName.endsWith(".hwp")) {
            throw new IllegalArgumentException("한글 문서 파일이 아닙니다.");
        }

        Path outputDir = sourceFile.getParent();
        if (outputDir == null) {
            throw new IllegalStateException("출력 디렉터리를 확인할 수 없습니다.");
        }

        String libreOfficePath = LibreOfficeSupport.resolveLibreOfficePath(storageProperties);
        if (!StringUtils.hasText(libreOfficePath)) {
            throw new IllegalStateException(
                    "LibreOffice가 설치되어 있지 않아 HWPX를 PDF로 변환할 수 없습니다."
            );
        }

        Path profileDir = storageProperties.getLibreOfficeProfileDir();
        try {
            Files.createDirectories(profileDir);
        } catch (IOException ex) {
            throw new IllegalStateException("LibreOffice 프로필 디렉터리를 만들 수 없습니다.", ex);
        }

        List<String> command = new ArrayList<>();
        command.add(libreOfficePath);
        command.add("--headless");
        command.add("--norestore");
        command.add("--nolockcheck");
        command.add("-env:UserInstallation=" + LibreOfficeSupport.toFileUri(profileDir));
        command.add("--infilter=" + HWP_INFILTER);
        command.add("--convert-to");
        command.add(PDF_EXPORT_FILTER);
        command.add("--outdir");
        command.add(outputDir.toAbsolutePath().toString());
        command.add(sourceFile.toAbsolutePath().toString());

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String processOutput = readProcessOutput(process.getInputStream());
            boolean finished = process.waitFor(
                    storageProperties.getConversionTimeoutSeconds(),
                    TimeUnit.SECONDS
            );
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("HWPX PDF 변환 시간이 초과되었습니다.");
            }
            if (process.exitValue() != 0) {
                log.warn("LibreOffice 변환 실패 output: {}", processOutput);
                throw new IllegalStateException(buildConversionFailureMessage(processOutput));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("HWPX PDF 변환 중 오류가 발생했습니다.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HWPX PDF 변환 중 오류가 발생했습니다.", ex);
        }

        Path pdfPath = resolvePdfOutputPath(sourceFile, outputDir);
        if (!Files.exists(pdfPath)) {
            throw new IllegalStateException(
                    "변환된 PDF 파일을 찾을 수 없습니다. 서버 재시작 후 H2Orestart 확장 설치 로그를 확인해 주세요."
            );
        }
        return pdfPath;
    }

    private Path resolvePdfOutputPath(Path sourceFile, Path outputDir) {
        String baseName = sourceFile.getFileName().toString();
        int dotIndex = baseName.lastIndexOf('.');
        String pdfName = (dotIndex > 0 ? baseName.substring(0, dotIndex) : baseName) + ".pdf";
        return outputDir.resolve(pdfName);
    }

    private String buildConversionFailureMessage(String processOutput) {
        String trimmed = processOutput == null ? "" : processOutput.trim();
        if (trimmed.contains("source file could not be loaded")
                || trimmed.contains("Error: Please verify input parameters")) {
            return "한글 파일을 열 수 없습니다. 서버에 H2Orestart 확장 설치가 필요합니다.";
        }
        if (!trimmed.isEmpty()) {
            return "HWPX PDF 변환에 실패했습니다. (" + trimmed + ")";
        }
        return "HWPX PDF 변환에 실패했습니다. LibreOffice H2Orestart 확장 설치 여부를 확인해 주세요.";
    }

    private String readProcessOutput(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
