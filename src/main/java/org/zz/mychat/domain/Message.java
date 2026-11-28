package org.zz.mychat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属房间 room.id */
    private Long roomId;

    /** 引用的消息 message.id（可为空） */
    private Long replyToId;

    /** 发送者 anon_id */
    private String senderId;

    private String nickname;

    private String avatarColor;

    /** text（系统消息不落库） */
    private String type;

    private String content;

    /** 是否已撤回 */
    private Boolean revoked;

    /** 发送时间戳（毫秒） */
    private Long createTime;

    public boolean isRevokedMessage() {
        return Boolean.TRUE.equals(this.revoked);
    }
}
