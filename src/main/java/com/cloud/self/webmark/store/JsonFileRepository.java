package com.cloud.self.webmark.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 基于 JSON 文件的通用数据仓库，替代 MyBatis-Plus。
 * 每个实体对应一个 JSON 文件，提供 CRUD、过滤、排序、分页功能。
 */
public class JsonFileRepository<T> {

    private static final Logger log = LoggerFactory.getLogger(JsonFileRepository.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private final File file;
    private final Class<T> entityClass;
    private final List<T> data;
    private final AtomicLong idSeq;
    private final String idFieldName;
    private final boolean prettyPrint;

    /**
     * @param file         JSON 文件路径
     * @param entityClass  实体类
     * @param prettyPrint  是否使用美化输出（false 则紧凑输出以节省空间和 I/O）
     */
    public JsonFileRepository(File file, Class<T> entityClass, boolean prettyPrint) {
        this.file = file;
        this.entityClass = entityClass;
        this.idFieldName = "id";
        this.prettyPrint = prettyPrint;
        // 读取已有数据或初始化空列表
        if (file.exists() && file.length() > 0) {
            try {
                List<?> rawLoaded = MAPPER.readValue(file, List.class);
                List<T> loaded = new ArrayList<>();
                if (rawLoaded != null) {
                    for (Object item : rawLoaded) {
                        if (entityClass.isInstance(item)) {
                            loaded.add(entityClass.cast(item));
                        } else {
                            // 类型不匹配（如旧版本写入的 LinkedHashMap），手动转换
                            loaded.add(MAPPER.convertValue(item, entityClass));
                        }
                    }
                }
                this.data = new CopyOnWriteArrayList<>(loaded);
            } catch (IOException e) {
                throw new RuntimeException("读取 JSON 文件失败: " + file.getAbsolutePath(), e);
            }
        } else {
            this.data = new CopyOnWriteArrayList<>();
        }
        // 计算当前最大 ID
        long maxId = 0;
        for (T item : data) {
            Long id = getIdValue(item);
            if (id != null && id > maxId) {
                maxId = id;
            }
        }
        this.idSeq = new AtomicLong(maxId);
    }

    // ==================== CRUD 基础方法 ====================

    /** 保存新实体（自动生成 ID） */
    public synchronized T save(T entity) {
        Long id = getIdValue(entity);
        if (id == null || id == 0) {
            id = idSeq.incrementAndGet();
            setIdValue(entity, id);
        } else {
            // 如果指定了 ID，确保 ID 序列跟上
            if (id > idSeq.get()) {
                idSeq.set(id);
            }
        }
        // 设置创建时间和更新时间
        setFieldIfPresent(entity, "createTime", LocalDateTime.now());
        setFieldIfPresent(entity, "updateTime", LocalDateTime.now());
        // 设置默认 deleted=0
        setFieldIfPresent(entity, "deleted", 0);

        data.add(entity);
        flush();
        return entity;
    }

    /** 批量保存 */
    public synchronized List<T> saveAll(List<T> entities) {
        for (T entity : entities) {
            save(entity);
        }
        return entities;
    }

    /** 根据 ID 查找 */
    public T findById(Long id) {
        return data.stream()
                .filter(e -> Objects.equals(getIdValue(e), id))
                .filter(e -> !isDeleted(e))
                .findFirst()
                .orElse(null);
    }

    /** 获取所有未被逻辑删除的记录 */
    public List<T> findAll() {
        return data.stream()
                .filter(e -> !isDeleted(e))
                .collect(Collectors.toList());
    }

    /** 获取全部记录（含已删除） */
    public List<T> findAllWithDeleted() {
        return new ArrayList<>(data);
    }

    /** 根据条件查询 */
    public List<T> find(Predicate<T> predicate) {
        return data.stream()
                .filter(e -> !isDeleted(e))
                .filter(predicate)
                .collect(Collectors.toList());
    }

    /** 查询单条 */
    public T findOne(Predicate<T> predicate) {
        return data.stream()
                .filter(e -> !isDeleted(e))
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    /** 更新实体 */
    public synchronized T update(T entity) {
        Long id = getIdValue(entity);
        if (id == null) return null;

        setFieldIfPresent(entity, "updateTime", LocalDateTime.now());

        for (int i = 0; i < data.size(); i++) {
            if (Objects.equals(getIdValue(data.get(i)), id)) {
                data.set(i, entity);
                flush();
                return entity;
            }
        }
        return null;
    }

    /** 根据 ID 逻辑删除 */
    public synchronized boolean deleteById(Long id) {
        for (int i = 0; i < data.size(); i++) {
            if (Objects.equals(getIdValue(data.get(i)), id)) {
                T entity = data.get(i);
                setFieldIfPresent(entity, "deleted", 1);
                data.set(i, entity);
                flush();
                return true;
            }
        }
        return false;
    }

    /** 根据 ID 物理删除 */
    public synchronized boolean physicalDeleteById(Long id) {
        boolean removed = data.removeIf(e -> Objects.equals(getIdValue(e), id));
        if (removed) {
            flush();
        }
        return removed;
    }

    /** 统计未删除的记录数 */
    public long count() {
        return data.stream().filter(e -> !isDeleted(e)).count();
    }

    /** 统计符合条件的记录数 */
    public long count(Predicate<T> predicate) {
        return data.stream()
                .filter(e -> !isDeleted(e))
                .filter(predicate)
                .count();
    }

    // ==================== 分页查询 ====================

    /** 分页查询全部 */
    public PageResult<T> page(int pageNum, int pageSize) {
        return page(pageNum, pageSize, t -> true);
    }

    /** 条件分页查询（pageNum/pageSize 自动 clamp 到合法值） */
    public PageResult<T> page(int pageNum, int pageSize, Predicate<T> predicate) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1) pageSize = 10;

        List<T> filtered = data.stream()
                .filter(e -> !isDeleted(e))
                .filter(predicate)
                .collect(Collectors.toList());

        long total = filtered.size();
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, filtered.size());

        List<T> records = from >= filtered.size() ? Collections.emptyList() : filtered.subList(from, to);
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    /** 排序分页查询（pageNum/pageSize 自动 clamp 到合法值） */
    public PageResult<T> pageOrderBy(int pageNum, int pageSize, Predicate<T> predicate,
                                     Comparator<T> comparator) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1) pageSize = 10;

        List<T> filtered = data.stream()
                .filter(e -> !isDeleted(e))
                .filter(predicate)
                .sorted(comparator)
                .collect(Collectors.toList());

        long total = filtered.size();
        int from = (pageNum - 1) * pageSize;
        int to = Math.min(from + pageSize, filtered.size());

        List<T> records = from >= filtered.size() ? Collections.emptyList() : filtered.subList(from, to);
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    // ==================== 批量操作 ====================

    /** 根据条件更新 */
    public synchronized int updateAll(Predicate<T> predicate, Consumer<T> updater) {
        int count = 0;
        for (int i = 0; i < data.size(); i++) {
            T entity = data.get(i);
            if (!isDeleted(entity) && predicate.test(entity)) {
                updater.accept(entity);
                setFieldIfPresent(entity, "updateTime", LocalDateTime.now());
                data.set(i, entity);
                count++;
            }
        }
        if (count > 0) {
            flush();
        }
        return count;
    }

    /** 获取排序后的列表 */
    public List<T> findAllSorted(Comparator<T> comparator) {
        return data.stream()
                .filter(e -> !isDeleted(e))
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    /** 获取 Top N */
    public List<T> top(int limit, Comparator<T> comparator) {
        return data.stream()
                .filter(e -> !isDeleted(e))
                .sorted(comparator)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** 清空并重置（保留文件存在） */
    public synchronized void clear() {
        data.clear();
        idSeq.set(0);
        flush();
    }

    /** 刷新数据到磁盘 */
    public synchronized void flush() {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            if (prettyPrint) {
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, data);
            } else {
                MAPPER.writeValue(file, data);
            }
        } catch (IOException e) {
            throw new RuntimeException("写入 JSON 文件失败: " + file.getAbsolutePath(), e);
        }
    }

    /** 判断是否已被逻辑删除 */
    private boolean isDeleted(T entity) {
        try {
            Field field = entityClass.getDeclaredField("deleted");
            field.setAccessible(true);
            Object val = field.get(entity);
            return val instanceof Number && ((Number) val).intValue() == 1;
        } catch (NoSuchFieldException e) {
            return false; // 没有 deleted 字段的实体不做逻辑删除
        } catch (IllegalAccessException e) {
            log.debug("isDeleted: 无法访问 deleted 字段 for entity {}", entityClass.getSimpleName(), e);
            return false;
        }
    }

    // ==================== 反射辅助 ====================

    private Long getIdValue(T entity) {
        try {
            Field field = entityClass.getDeclaredField(idFieldName);
            field.setAccessible(true);
            Object val = field.get(entity);
            if (val instanceof Number) {
                return ((Number) val).longValue();
            }
            return null;
        } catch (NoSuchFieldException e) {
            log.debug("getIdValue: entity {} 没有 {} 字段", entityClass.getSimpleName(), idFieldName);
            return null;
        } catch (IllegalAccessException e) {
            log.debug("getIdValue: 无法访问 {} 字段 for entity {}", idFieldName, entityClass.getSimpleName(), e);
            return null;
        }
    }

    private void setIdValue(T entity, Long id) {
        try {
            Field field = entityClass.getDeclaredField(idFieldName);
            field.setAccessible(true);
            field.set(entity, id);
        } catch (NoSuchFieldException e) {
            log.debug("setIdValue: entity {} 没有 {} 字段", entityClass.getSimpleName(), idFieldName);
        } catch (IllegalAccessException e) {
            log.debug("setIdValue: 无法设置 {} 字段 for entity {}", idFieldName, entityClass.getSimpleName(), e);
        }
    }

    private void setFieldIfPresent(T entity, String fieldName, Object value) {
        try {
            Field field = entityClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (NoSuchFieldException ignored) {
            // 字段不存在就跳过
        } catch (IllegalAccessException e) {
            log.debug("setFieldIfPresent: 无法访问字段 {} for entity {}", fieldName, entityClass.getSimpleName(), e);
        }
    }

    /** 获取当前数据大小 */
    public int size() {
        return data.size();
    }
}
