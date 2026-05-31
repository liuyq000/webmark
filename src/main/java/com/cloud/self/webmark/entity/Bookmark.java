package com.cloud.self.webmark.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Bookmark {

    private Long id;

    private Long userId;

    private Long folderId;

    private Long favoritesId;

    private String title;

    private String url;

    private String description;

    private String logoUrl;

    private String tags;

    private Integer viewCount;

    private Integer collectCount;

    private Integer sortOrder;

    private Integer publicType;

    private Integer reviewStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
