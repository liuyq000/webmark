package com.cloud.self.webmark.service;

import com.cloud.self.webmark.config.DataStore;
import com.cloud.self.webmark.entity.UrlLibrary;
import com.cloud.self.webmark.store.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class UrlLibraryService {

    private final DataStore dataStore;

    public PageResult<UrlLibrary> adminPage(int pageNum, int pageSize, String keyword) {
        return dataStore.getUrlLibraryRepository().pageOrderBy(pageNum, pageSize,
                u -> {
                    if (keyword == null || keyword.isEmpty()) return true;
                    return (u.getTitle() != null && u.getTitle().contains(keyword))
                            || (u.getUrl() != null && u.getUrl().contains(keyword));
                },
                Comparator.comparing(UrlLibrary::getCreateTime).reversed());
    }

    public java.util.List<UrlLibrary> list() {
        return dataStore.getUrlLibraryRepository().findAll();
    }

    public UrlLibrary getById(Long id) {
        return dataStore.getUrlLibraryRepository().findById(id);
    }

    public boolean save(UrlLibrary urlLibrary) {
        dataStore.getUrlLibraryRepository().save(urlLibrary);
        return true;
    }

    public boolean updateById(UrlLibrary urlLibrary) {
        return dataStore.getUrlLibraryRepository().update(urlLibrary) != null;
    }

    public boolean removeById(Long id) {
        return dataStore.getUrlLibraryRepository().deleteById(id);
    }
}
