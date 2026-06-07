package com.cloud.self.webmark.service;

import com.cloud.self.webmark.config.DataStore;
import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.store.PageResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BookmarkService {

    private final DataStore dataStore;

    public BookmarkService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    private Comparator<Bookmark> defaultSort() {
        return Comparator.<Bookmark, Integer>comparing(b -> b.getSortOrder() != null ? b.getSortOrder() : 9999)
                .thenComparing(Comparator.comparing(Bookmark::getCreateTime).reversed());
    }

    public List<Bookmark> listPublicByFolderIds(List<Long> folderIds) {
        return dataStore.getBookmarkRepository().findAllSorted(defaultSort())
                .stream()
                .filter(b -> b.getPublicType() == 1)
                .filter(b -> folderIds == null || folderIds.isEmpty() || (b.getFolderId() != null && folderIds.contains(b.getFolderId())))
                .collect(Collectors.toList());
    }

    public List<Bookmark> listPublicByFolderId(Long folderId) {
        return dataStore.getBookmarkRepository().findAllSorted(defaultSort())
                .stream()
                .filter(b -> b.getPublicType() == 1)
                .filter(b -> folderId == null || folderId.equals(b.getFolderId()))
                .collect(Collectors.toList());
    }

    public static Predicate<Bookmark> buildFilter(String keyword, Long folderId, List<Long> folderIds, Integer publicType, Long userId) {
        return b -> {
            boolean match = true;
            if (keyword != null && !keyword.isEmpty()) {
                match = (b.getTitle() != null && b.getTitle().contains(keyword))
                        || (b.getUrl() != null && b.getUrl().contains(keyword))
                        || (b.getDescription() != null && b.getDescription().contains(keyword))
                        || (b.getTags() != null && b.getTags().contains(keyword));
            }
            if (match && folderIds != null && !folderIds.isEmpty()) {
                match = b.getFolderId() != null && folderIds.contains(b.getFolderId());
            }
            if (match && folderId != null && folderIds == null) {
                match = folderId.equals(b.getFolderId());
            }
            if (match && publicType != null) {
                match = publicType.equals(b.getPublicType());
            }
            if (match && userId != null) {
                match = b.getUserId() == null || userId.equals(b.getUserId());
            }
            return match;
        };
    }

    public PageResult<Bookmark> adminPage(int pageNum, int pageSize, String keyword, Long folderId, Integer publicType, Long userId) {
        return dataStore.getBookmarkRepository().pageOrderBy(pageNum, pageSize,
                buildFilter(keyword, folderId, null, publicType, userId),
                Comparator.comparing(Bookmark::getCreateTime).reversed());
    }

    public PageResult<Bookmark> adminPageByFolder(int pageNum, int pageSize, String keyword, Long folderId, Integer publicType, FolderService folderService, Long userId) {
        List<Long> folderIds = folderId != null ? folderService.getDescendantIds(folderId) : null;
        return dataStore.getBookmarkRepository().pageOrderBy(pageNum, pageSize,
                buildFilter(keyword, null, folderIds, publicType, userId),
                Comparator.comparing(Bookmark::getCreateTime).reversed());
    }

    public List<Bookmark> searchPublic(String key) {
        String lowerKey = key.toLowerCase();
        return dataStore.getBookmarkRepository().findAllSorted(defaultSort())
                .stream()
                .filter(b -> b.getPublicType() == 1)
                .filter(b -> (b.getTitle() != null && b.getTitle().toLowerCase().contains(lowerKey))
                        || (b.getDescription() != null && b.getDescription().toLowerCase().contains(lowerKey))
                        || (b.getTags() != null && b.getTags().toLowerCase().contains(lowerKey))
                        || (b.getUrl() != null && b.getUrl().toLowerCase().contains(lowerKey)))
                .collect(Collectors.toList());
    }

    public long countPublic() {
        return dataStore.getBookmarkRepository().count(b -> b.getPublicType() == 1);
    }

    public long countToday() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        return dataStore.getBookmarkRepository().count(b -> {
            LocalDateTime ct = b.getCreateTime();
            return ct != null && !ct.isBefore(start) && !ct.isAfter(end);
        });
    }

    public List<Bookmark> topHot(int limit) {
        return dataStore.getBookmarkRepository().top(limit,
                Comparator.comparing(Bookmark::getCreateTime).reversed())
                .stream()
                .filter(b -> b.getPublicType() == 1)
                .collect(Collectors.toList());
    }

    public List<Bookmark> listByUserId(Long userId) {
        return dataStore.getBookmarkRepository().findAllSorted(Comparator.comparing(Bookmark::getCreateTime).reversed())
                .stream()
                .filter(b -> userId.equals(b.getUserId()))
                .collect(Collectors.toList());
    }

    public List<Bookmark> listByFolderIds(List<Long> folderIds) {
        return dataStore.getBookmarkRepository().findAllSorted(defaultSort())
                .stream()
                .filter(b -> folderIds == null || folderIds.isEmpty() || (b.getFolderId() != null && folderIds.contains(b.getFolderId())))
                .collect(Collectors.toList());
    }

    public long count() { return dataStore.getBookmarkRepository().count(); }
    public Bookmark getById(Long id) { return dataStore.getBookmarkRepository().findById(id); }
    public boolean save(Bookmark bookmark) { dataStore.getBookmarkRepository().save(bookmark); return true; }
    public boolean updateById(Bookmark bookmark) { return dataStore.getBookmarkRepository().update(bookmark) != null; }
    public boolean removeById(Long id) { return dataStore.getBookmarkRepository().deleteById(id); }

    public int removeByFolderIds(List<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) return 0;
        return dataStore.getBookmarkRepository().updateAll(
                b -> b.getFolderId() != null && folderIds.contains(b.getFolderId()),
                b -> b.setDeleted(1)
        );
    }
}
