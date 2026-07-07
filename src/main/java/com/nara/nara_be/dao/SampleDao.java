package com.nara.nara_be.dao;

import com.nara.nara_be.domain.Sample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SampleDao {

    List<Sample> findAll();

    Sample findById(@Param("id") Long id);

    boolean existsById(@Param("id") Long id);

    void insert(Sample sample);

    void update(Sample sample);

    void deleteById(@Param("id") Long id);
}
