package com.nara.nara_be.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleDriveUpdateFileRequest {

    private String name;
    private String parentId;
}
