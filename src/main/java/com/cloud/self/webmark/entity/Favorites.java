package com.cloud.self.webmark.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Favorites {

    private Long id;

    private Long userId;

    private String name;

    private String description;

    private Integer count;

    private Integer publicType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
