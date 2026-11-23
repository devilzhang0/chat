package org.zz.mychat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zz.mychat.domain.User;
import org.zz.mychat.mapper.UserMapper;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestService {

    private final UserMapper userMapper;

    public String newAnonId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public User findByAnonId(String anonId) {
        if (anonId == null || anonId.isBlank()) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getAnonId, anonId));
    }

    /** 按 anonId 新建或更新游客记录，返回用户行 */
    public User upsert(String anonId, String nickname, String avatarColor, String ip) {
        LocalDateTime now = LocalDateTime.now();
        User user = findByAnonId(anonId);
        if (user == null) {
            user = new User();
            user.setAnonId(anonId);
            user.setNickname(nickname);
            user.setAvatarColor(avatarColor);
            user.setIp(ip);
            user.setFirstSeenAt(now);
            user.setLastSeenAt(now);
            userMapper.insert(user);
        } else {
            user.setNickname(nickname);
            user.setAvatarColor(avatarColor);
            user.setIp(ip);
            user.setLastSeenAt(now);
            userMapper.updateById(user);
        }
        return user;
    }
}
