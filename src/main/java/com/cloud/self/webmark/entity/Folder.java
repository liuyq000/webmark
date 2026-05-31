package com.cloud.self.webmark.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Folder {

    private Long id;

    private Long userId;

    private Long parentId;

    private String name;

    private String nameEn;

    private String icon;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;

    /** 子文件夹列表 */
    private List<Folder> children;
}
