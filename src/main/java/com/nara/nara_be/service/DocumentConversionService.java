package com.nara.nara_be.service;

import java.nio.file.Path;

public interface DocumentConversionService {

    Path convertHancomDocumentToPdf(Path sourceFile);
}
