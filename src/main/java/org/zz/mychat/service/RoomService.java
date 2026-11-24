package org.zz.mychat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.zz.mychat.domain.Room;
import org.zz.mychat.mapper.RoomMapper;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final int MIN_NAME_LEN = 1;
    private static final int MAX_NAME_LEN = 24;
    private static final int MIN_PASSWORD_LEN = 4;
    private static final int MAX_PASSWORD_LEN = 20;
    private static final int MAX_CODE_RETRY = 10;

    private final RoomMapper roomMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public Room createRoom(String rawName, boolean isPrivate, String rawPassword, String creatorAnonId) {
        String name = rawName == null ? "" : rawName.trim();
        int nameLen = name.codePointCount(0, name.length());
        if (nameLen < MIN_NAME_LEN || nameLen > MAX_NAME_LEN) {
            throw new IllegalArgumentException("房间名称长度需在 1 到 24 个字符之间");
        }

        String passwordHash = null;
        if (isPrivate) {
            String pwd = rawPassword == null ? "" : rawPassword;
            int pwdLen = pwd.codePointCount(0, pwd.length());
            if (pwd.isBlank() || pwdLen < MIN_PASSWORD_LEN || pwdLen > MAX_PASSWORD_LEN) {
                throw new IllegalArgumentException("私密房间密码长度需在 4 到 20 个字符之间");
            }
            passwordHash = passwordEncoder.encode(pwd);
        }

        for (int attempt = 0; attempt < MAX_CODE_RETRY; attempt++) {
            String code = generateSixDigitCode();
            if (findByCode(code) != null) {
                continue;
            }
            Room room = new Room();
            room.setCode(code);
            room.setName(name);
            room.setIsPrivate(isPrivate);
            room.setPasswordHash(passwordHash);
            room.setCreatorAnonId(creatorAnonId);
            room.setCreatedAt(LocalDateTime.now());
            try {
                roomMapper.insert(room);
                return room;
            } catch (DuplicateKeyException ignored) {
                // 极小概率并发撞号，重试生成新房间号
            }
        }
        throw new IllegalStateException("生成房间号失败，请稍后重试");
    }

    public Room findByCode(String rawCode) {
        if (rawCode == null) {
            return null;
        }
        String code = rawCode.trim();
        if (code.isEmpty()) {
            return null;
        }
        return roomMapper.selectOne(new LambdaQueryWrapper<Room>().eq(Room::getCode, code));
    }

    public List<Room> findByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return roomMapper.selectList(new LambdaQueryWrapper<Room>().in(Room::getCode, codes));
    }

    public boolean verifyPassword(Room room, String rawPassword) {
        if (room == null) {
            return false;
        }
        if (!room.isPrivateRoom()) {
            return true;
        }
        if (rawPassword == null || rawPassword.isEmpty() || room.getPasswordHash() == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, room.getPasswordHash());
    }

    private String generateSixDigitCode() {
        int num = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(num);
    }
}
