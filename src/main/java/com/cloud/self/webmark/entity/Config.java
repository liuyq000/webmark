package com.cloud.self.webmark.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Config {

    private Long id;

    private Long userId;

    private String defaultFavorites;

    private String defaultModel;

    private LocalDateTime updateTime;
}
