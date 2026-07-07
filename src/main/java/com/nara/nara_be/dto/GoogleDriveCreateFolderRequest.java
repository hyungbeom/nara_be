package com.nara.nara_be.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleDriveCreateFolderRequest {

    private String name;
    private String parentId;
}
