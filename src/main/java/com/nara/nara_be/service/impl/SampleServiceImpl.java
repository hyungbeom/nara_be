package com.nara.nara_be.service.impl;

import com.nara.nara_be.dao.SampleDao;
import com.nara.nara_be.domain.Sample;
import com.nara.nara_be.dto.SampleRequest;
import com.nara.nara_be.dto.SampleResponse;
import com.nara.nara_be.exception.BusinessException;
import com.nara.nara_be.service.SampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SampleServiceImpl implements SampleService {

    private final SampleDao sampleDao;

    @Override
    public List<SampleResponse> findAll() {
        return sampleDao.findAll().stream()
                .map(SampleResponse::from)
                .toList();
    }

    @Override
    public SampleResponse findById(Long id) {
        Sample sample = sampleDao.findById(id);
        if (sample == null) {
            throw new BusinessException("Sample not found: " + id, HttpStatus.NOT_FOUND);
        }
        return SampleResponse.from(sample);
    }

    @Override
    @Transactional
    public SampleResponse create(SampleRequest request) {
        Sample sample = request.toEntity();
        sampleDao.insert(sample);
        return SampleResponse.from(sample);
    }

    @Override
    @Transactional
    public SampleResponse update(Long id, SampleRequest request) {
        Sample sample = sampleDao.findById(id);
        if (sample == null) {
            throw new BusinessException("Sample not found: " + id, HttpStatus.NOT_FOUND);
        }
        sample.update(request.getName(), request.getDescription());
        sampleDao.update(sample);
        return SampleResponse.from(sample);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!sampleDao.existsById(id)) {
            throw new BusinessException("Sample not found: " + id, HttpStatus.NOT_FOUND);
        }
        sampleDao.deleteById(id);
    }
}
