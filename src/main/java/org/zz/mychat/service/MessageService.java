package org.zz.mychat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zz.mychat.domain.Message;
import org.zz.mychat.mapper.MessageMapper;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

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
        List<Message> list = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getRoomId, roomId)
                        .orderByDesc(Message::getId)
                        .last("limit " + limit));
        Collections.reverse(list);
        return list;
    }
}
