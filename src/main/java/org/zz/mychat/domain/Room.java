package org.zz.mychat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("room")
public class Room {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 6位唯一房间号 */
    private String code;

    /** 房间名称 */
    private String name;

    /** 是否私密房 */
    private Boolean isPrivate;

    /** 私密房密码 BCrypt 哈希 */
    private String passwordHash;

    /** 创建者 anon_id */
    private String creatorAnonId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public boolean isPrivateRoom() {
        return Boolean.TRUE.equals(this.isPrivate);
    }
}
