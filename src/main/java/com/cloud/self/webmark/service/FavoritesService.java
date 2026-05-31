package com.cloud.self.webmark.service;

import com.cloud.self.webmark.config.DataStore;
import com.cloud.self.webmark.entity.Favorites;
import com.cloud.self.webmark.store.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class FavoritesService {

    private final DataStore dataStore;

    public PageResult<Favorites> adminPage(int pageNum, int pageSize, String keyword, Long userId) {
        return dataStore.getFavoritesRepository().pageOrderBy(pageNum, pageSize,
                f -> {
                    boolean match = true;
                    if (keyword != null && !keyword.isEmpty()) {
                        match = f.getName() != null && f.getName().contains(keyword);
                    }
                    if (match && userId != null) {
                        match = userId.equals(f.getUserId());
                    }
                    return match;
                },
                Comparator.comparing(Favorites::getCreateTime).reversed());
    }

    public java.util.List<Favorites> list() {
        return dataStore.getFavoritesRepository().findAll();
    }
}
