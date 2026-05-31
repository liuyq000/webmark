package com.cloud.self.webmark.service;

import com.cloud.self.webmark.config.DataStore;
import com.cloud.self.webmark.entity.Folder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final DataStore dataStore;

    /** 获取所有顶级文件夹（带子文件夹）——管理后台用 */
    public List<Folder> listTree() {
        List<Folder> all = dataStore.getFolderRepository().findAllSorted(Comparator.comparing(Folder::getSortOrder));
        return buildTree(all);
    }

    /** 获取公共文件夹树（userId为null的）——匿名用户用 */
    public List<Folder> listPublicTree() {
        List<Folder> all = dataStore.getFolderRepository().findAllSorted(Comparator.comparing(Folder::getSortOrder))
                .stream()
                .filter(f -> f.getUserId() == null)
                .collect(Collectors.toList());
        return buildTree(all);
    }

    /** 获取指定用户的文件夹树（含公共文件夹）——登录用户用 */
    public List<Folder> listTreeByUserId(Long userId) {
        List<Folder> all = dataStore.getFolderRepository().findAllSorted(Comparator.comparing(Folder::getSortOrder))
                .stream()
                .filter(f -> f.getUserId() == null || userId.equals(f.getUserId()))
                .collect(Collectors.toList());
        return buildTree(all);
    }

    /** 获取指定顶级文件夹及其子文件夹 */
    public List<Folder> listTree(Long parentId) {
        Folder parent = getById(parentId);
        if (parent == null) return new ArrayList<>();
        List<Folder> children = dataStore.getFolderRepository().findAllSorted(Comparator.comparing(Folder::getSortOrder))
                .stream()
                .filter(f -> parentId.equals(f.getParentId()))
                .collect(Collectors.toList());
        parent.setChildren(children);
        List<Folder> tree = new ArrayList<>();
        tree.add(parent);
        return tree;
    }

    /** 获取指定文件夹的所有后代ID（含自身） */
    public List<Long> getDescendantIds(Long folderId) {
        List<Long> ids = new ArrayList<>();
        ids.add(folderId);
        collectChildIds(folderId, ids);
        return ids;
    }

    private void collectChildIds(Long parentId, List<Long> ids) {
        List<Folder> children = dataStore.getFolderRepository().findAll()
                .stream()
                .filter(f -> parentId.equals(f.getParentId()))
                .collect(Collectors.toList());
        for (Folder child : children) {
            ids.add(child.getId());
            collectChildIds(child.getId(), ids);
        }
    }

    private List<Folder> buildTree(List<Folder> all) {
        Map<Long, List<Folder>> grouped = all.stream()
                .filter(f -> f.getParentId() != null)
                .collect(Collectors.groupingBy(Folder::getParentId, LinkedHashMap::new, Collectors.toList()));

        List<Folder> roots = all.stream()
                .filter(f -> f.getParentId() == null)
                .collect(Collectors.toList());

        for (Folder root : roots) {
            root.setChildren(buildChildren(root.getId(), grouped));
        }
        return roots;
    }

    private List<Folder> buildChildren(Long parentId, Map<Long, List<Folder>> grouped) {
        List<Folder> children = grouped.getOrDefault(parentId, new ArrayList<>());
        for (Folder child : children) {
            child.setChildren(buildChildren(child.getId(), grouped));
        }
        return children;
    }

    public Folder getById(Long id) {
        return dataStore.getFolderRepository().findById(id);
    }

    public boolean save(Folder folder) {
        dataStore.getFolderRepository().save(folder);
        return true;
    }

    public boolean updateById(Folder folder) {
        return dataStore.getFolderRepository().update(folder) != null;
    }

    public boolean removeById(Long id) {
        return dataStore.getFolderRepository().deleteById(id);
    }

    /** 删除文件夹及其所有后代文件夹（级联逻辑删除） */
    public int removeCascade(Long folderId) {
        List<Long> allIds = getDescendantIds(folderId);
        return dataStore.getFolderRepository().updateAll(
                f -> allIds.contains(f.getId()),
                f -> f.setDeleted(1)
        );
    }

    public long count() {
        return dataStore.getFolderRepository().count();
    }
}
