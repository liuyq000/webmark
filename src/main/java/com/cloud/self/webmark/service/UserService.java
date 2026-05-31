package com.cloud.self.webmark.service;

import com.cloud.self.webmark.config.DataStore;
import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.store.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final DataStore dataStore;
    private final PasswordEncoder passwordEncoder;

    public User findByUserName(String userName) {
        return dataStore.getUserRepository().findOne(u -> userName.equals(u.getUserName()));
    }

    public User findByEmail(String email) {
        return dataStore.getUserRepository().findOne(u -> email.equals(u.getEmail()));
    }

    public boolean register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("ROLE_USER");
        dataStore.getUserRepository().save(user);
        return true;
    }

    public PageResult<User> pageList(int pageNum, int pageSize, String keyword) {
        return dataStore.getUserRepository().pageOrderBy(pageNum, pageSize,
                u -> {
                    if (keyword == null || keyword.isEmpty()) return true;
                    return (u.getUserName() != null && u.getUserName().contains(keyword))
                            || (u.getEmail() != null && u.getEmail().contains(keyword));
                },
                Comparator.comparing(User::getCreateTime).reversed());
    }

    public long count() {
        return dataStore.getUserRepository().count();
    }

    public List<User> list() {
        return dataStore.getUserRepository().findAll();
    }

    public User getById(Long id) {
        return dataStore.getUserRepository().findById(id);
    }

    public boolean updateById(User user) {
        return dataStore.getUserRepository().update(user) != null;
    }

    public boolean removeById(Long id) {
        return dataStore.getUserRepository().deleteById(id);
    }
}
