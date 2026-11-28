package org.zz.mychat.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@TableName("user")
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @TableId
    private Long id;

    private String name;
    private Integer age;
    private String email;

    /** 游客匿名 ID（guest_id cookie 值），唯一标识 */
    private String anonId;

    /** 昵称 */
    private String nickname;

    /** 头像颜色 */
    private String avatarColor;

    /** 最近登录 IP */
    private String ip;

    /** 首次进入时间 */
    private LocalDateTime firstSeenAt;

    /** 最近进入时间 */
    private LocalDateTime lastSeenAt;
}
