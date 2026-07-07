package com.nara.nara_be.dao;

import com.nara.nara_be.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserDao {

    User findByUserId(@Param("userId") String userId);

    boolean existsByUserId(@Param("userId") String userId);

    void insert(User user);
}
