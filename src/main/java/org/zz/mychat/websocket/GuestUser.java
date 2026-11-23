package org.zz.mychat.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** WebSocket 在线会话对应的游客信息 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestUser {

    /** 数据库 user.id */
    private Long userId;

    /** anonId，对外标识 */
    private String id;

    private String nickname;

    private String avatarColor;

    private long joinedAt;

    private String ip;
}
