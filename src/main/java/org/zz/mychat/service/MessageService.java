package org.zz.mychat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zz.mychat.domain.Message;
import org.zz.mychat.mapper.MessageMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int REPLY_SNIPPET_MAX_LEN = 60;

    private final MessageMapper messageMapper;

    public record HistoryPage(List<Message> messages, boolean hasMore) {
    }

    public Message save(Long roomId, String senderId, String nickname, String avatarColor, String content) {
        return save(roomId, senderId, nickname, avatarColor, content, null);
    }

    public Message save(Long roomId, String senderId, String nickname, String avatarColor, String content, Long replyToId) {
        Message message = new Message();
        message.setRoomId(roomId);
        message.setReplyToId(replyToId);
        message.setSenderId(senderId);
        message.setNickname(nickname);
        message.setAvatarColor(avatarColor);
        message.setType("text");
        message.setContent(content);
        message.setCreateTime(System.currentTimeMillis());
        messageMapper.insert(message);
        return message;
    }

    /** 校验并查询同一房间内的目标消息（严格隔离跨房间引用） */
    public Message findByIdInRoom(Long roomId, Long messageId) {
        if (roomId == null || messageId == null || messageId <= 0) {
            return null;
        }
        return messageMapper.selectOne(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getRoomId, roomId)
                        .eq(Message::getId, messageId));
    }

    /** 批量查询同一房间内被引用的消息映射，避免历史列表 N+1 查询 */
    public Map<Long, Message> findMapByIdsInRoom(Long roomId, Collection<Long> ids) {
        if (roomId == null || ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<Long> validIds = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                validIds.add(id);
            }
        }
        if (validIds.isEmpty()) {
            return Map.of();
        }
        List<Message> found = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getRoomId, roomId)
                        .in(Message::getId, validIds));
        if (found == null || found.isEmpty()) {
            return Map.of();
        }
        Map<Long, Message> map = new LinkedHashMap<>();
        for (Message m : found) {
            if (m != null && m.getId() != null) {
                map.put(m.getId(), m);
            }
        }
        return map;
    }

    /** 生成单行引用预览摘要（折行转空格并限制长度） */
    public static String toReplySnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String singleLine = content.replaceAll("\\s+", " ").trim();
        int count = singleLine.codePointCount(0, singleLine.length());
        if (count <= REPLY_SNIPPET_MAX_LEN) {
            return singleLine;
        }
        int endIndex = singleLine.offsetByCodePoints(0, REPLY_SNIPPET_MAX_LEN);
        return singleLine.substring(0, endIndex) + "…";
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
