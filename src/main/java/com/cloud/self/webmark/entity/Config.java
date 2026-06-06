package com.cloud.self.webmark.entity;

import java.time.LocalDateTime;

public class Config {

    private Long id;
    private Long userId;
    private String defaultFavorites;
    private String defaultModel;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDefaultFavorites() { return defaultFavorites; }
    public void setDefaultFavorites(String defaultFavorites) { this.defaultFavorites = defaultFavorites; }
    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
