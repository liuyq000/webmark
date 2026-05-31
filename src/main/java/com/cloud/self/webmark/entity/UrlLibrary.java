package com.cloud.self.webmark.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UrlLibrary {

    private Long id;

    private String url;

    private String title;

    private String description;

    private String icon;

    private Integer viewCount;

    private LocalDateTime createTime;

    private Integer deleted;
}
