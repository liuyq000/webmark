package com.cloud.self.webmark.service;

import com.cloud.self.webmark.config.DataStore;
import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.store.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final DataStore dataStore;

    /** 默认排序：sortOrder ASC → createTime DESC */
    private Comparator<Bookmark> defaultSort() {
        return Comparator.<Bookmark, Integer>comparing(b -> b.getSortOrder() != null ? b.getSortOrder() : 9999)
                .thenComparing(Comparator.comparing(Bookmark::getCreateTime).reversed());
    }

    /** 按文件夹ID列表查询公开书签 */
    public List<Bookmark> listPublicByFolderIds(List<Long> folderIds) {
        return dataStore.getBookmarkRepository().findAllSorted(defaultSort())
                .stream()
                .filter(b -> b.getPublicType() == 1)
                .filter(b -> folderIds == null || folderIds.isEmpty() || (b.getFolderId() != null && folderIds.contains(b.getFolderId())))
                .collect(Collectors.toList());
    }

    /** 按单个文件夹ID查询公开书签 */
    public List<Bookmark> listPublicByFolderId(Long folderId) {
        return dataStore.getBookmarkRepository().findAllSorted(defaultSort())
                .stream()
                .filter(b -> b.getPublicType() == 1)
                .filter(b -> folderId == null || folderId.equals(b.getFolderId()))
                .collect(Collectors.toList());
    }

    /** 管理后台分页查询 */
    public PageResult<Bookmark> adminPage(int pageNum, int pageSize, String keyword, Long folderId, Integer publicType) {
        return dataStore.getBookmarkRepository().pageOrderBy(pageNum, pageSize,
                b -> {
                    boolean match = true;
                    if (keyword != null && !keyword.isEmpty()) {
                        match = (b.getTitle() != null && b.getTitle().contains(keyword))
                                || (b.getUrl() != null && b.getUrl().contains(keyword))
                                || (b.getDescription() != null && b.getDescription().contains(keyword))
                                || (b.getTags() != null && b.getTags().contains(keyword));
                    }
                    if (match && folderId != null) {
                        match = folderId.equals(b.getFolderId());
                    }
                    if (match && publicType != null) {
                        match = publicType.equals(b.getPublicType());
                    }
                    return match;
                },
                Comparator.comparing(Bookmark::getCreateTime).reversed());
    }

    /** 管理后台分页查询（按文件夹及子文件夹） */
    public PageResult<Bookmark> adminPageByFolder(int pageNum, int pageSize, String keyword, Long folderId, Integer publicType, FolderService folderService) {
        List<Long> folderIds = folderId != null ? folderService.getDescendantIds(folderId) : null;
        return dataStore.getBookmarkRepository().pageOrderBy(pageNum, pageSize,
                b -> {
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
                    if (match && publicType != null) {
                        match = publicType.equals(b.getPublicType());
                    }
                    return match;
                },
                Comparator.comparing(Bookmark::getCreateTime).reversed());
    }

    /** 搜索公开书签 */
    public List<Bookmark> searchPublic(String key) {
        String lowerKey = key.toLowerCase();
        return dataStore.getBookmarkRepository().findAllSorted(defaultSort())
                .stream()
                .filter(b -> b.getPublicType() == 1)
                .filter(b -> (b.getTitle() != null && b.getTitle().toLowerCase().contains(lowerKey))
                        || (b.getDescription() != null && b.getDescription().toLowerCase().contains(lowerKey))
                        || (b.getTags() != null && b.getTags().toLowerCase().contains(lowerKey)))
                .collect(Collectors.toList());
    }

    /** 公开书签总数 */
    public long countPublic() {
        return dataStore.getBookmarkRepository().count(b -> b.getPublicType() == 1);
    }

    /** 今日新增 */
    public long countToday() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        return dataStore.getBookmarkRepository().count(b -> {
            LocalDateTime ct = b.getCreateTime();
            return ct != null && !ct.isBefore(start) && !ct.isAfter(end);
        });
    }

    /** 热门书签 */
    public List<Bookmark> topHot(int limit) {
        return dataStore.getBookmarkRepository().top(limit,
                Comparator.comparing(Bookmark::getCreateTime).reversed())
                .stream()
                .filter(b -> b.getPublicType() == 1)
                .collect(Collectors.toList());
    }

    /** 按用户查询书签 */
    public List<Bookmark> listByUserId(Long userId) {
        return dataStore.getBookmarkRepository().findAllSorted(Comparator.comparing(Bookmark::getCreateTime).reversed())
                .stream()
                .filter(b -> userId.equals(b.getUserId()))
                .collect(Collectors.toList());
    }

    /** 按文件夹ID列表查询所有书签（不限制公开状态） */
    public List<Bookmark> listByFolderIds(List<Long> folderIds) {
        return dataStore.getBookmarkRepository().findAllSorted(defaultSort())
                .stream()
                .filter(b -> folderIds == null || folderIds.isEmpty() || (b.getFolderId() != null && folderIds.contains(b.getFolderId())))
                .collect(Collectors.toList());
    }

    public long count() {
        return dataStore.getBookmarkRepository().count();
    }

    public Bookmark getById(Long id) {
        return dataStore.getBookmarkRepository().findById(id);
    }

    public boolean save(Bookmark bookmark) {
        dataStore.getBookmarkRepository().save(bookmark);
        return true;
    }

    public boolean updateById(Bookmark bookmark) {
        return dataStore.getBookmarkRepository().update(bookmark) != null;
    }

    public boolean removeById(Long id) {
        return dataStore.getBookmarkRepository().deleteById(id);
    }

    /** 按文件夹ID列表批量逻辑删除书签 */
    public int removeByFolderIds(List<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) return 0;
        return dataStore.getBookmarkRepository().updateAll(
                b -> b.getFolderId() != null && folderIds.contains(b.getFolderId()),
                b -> b.setDeleted(1)
        );
    }
}
