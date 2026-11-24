package org.zz.mychat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zz.mychat.domain.Message;
import org.zz.mychat.mapper.MessageMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    public record HistoryPage(List<Message> messages, boolean hasMore) {
    }

    public Message save(Long roomId, String senderId, String nickname, String avatarColor, String content) {
        Message message = new Message();
        message.setRoomId(roomId);
        message.setSenderId(senderId);
        message.setNickname(nickname);
        message.setAvatarColor(avatarColor);
        message.setType("text");
        message.setContent(content);
        message.setCreateTime(System.currentTimeMillis());
        messageMapper.insert(message);
        return message;
    }

    /** 指定房间最近 limit 条，按时间升序返回（旧→新） */
    public List<Message> latest(Long roomId, int limit) {
        return latestPage(roomId, limit).messages();
    }

    /** 指定房间最近 limit 条（多查 1 条判断 hasMore），按时间升序返回（旧→新） */
    public HistoryPage latestPage(Long roomId, int limit) {
        int safeLimit = clampLimit(limit);
        List<Message> list = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getRoomId, roomId)
                        .orderByDesc(Message::getId)
                        .last("limit " + (safeLimit + 1)));
        return toAscendingPage(list, safeLimit);
    }

    /** 按游标 beforeId 向上分页拉取更早的历史消息（多查 1 条判断 hasMore），按时间升序返回（旧→新） */
    public HistoryPage beforePage(Long roomId, Long beforeId, int limit) {
        if (beforeId == null || beforeId <= 0) {
            return new HistoryPage(List.of(), false);
        }
        int safeLimit = clampLimit(limit);
        List<Message> list = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getRoomId, roomId)
                        .lt(Message::getId, beforeId)
                        .orderByDesc(Message::getId)
                        .last("limit " + (safeLimit + 1)));
        return toAscendingPage(list, safeLimit);
    }

    private HistoryPage toAscendingPage(List<Message> descList, int limit) {
        if (descList == null || descList.isEmpty()) {
            return new HistoryPage(List.of(), false);
        }
        boolean hasMore = descList.size() > limit;
        List<Message> slice = hasMore ? new ArrayList<>(descList.subList(0, limit)) : new ArrayList<>(descList);
        Collections.reverse(slice);
        return new HistoryPage(slice, hasMore);
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
