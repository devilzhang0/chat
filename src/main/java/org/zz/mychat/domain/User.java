package org.zz.mychat.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@TableName("user")
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @TableId
    private Long id;

    private String name;
    private Integer age;
    private String email;

    /** 仅用于 Spring Security UserDetails（硬编码 admin），不映射数据库列 */
    @TableField(exist = false)
    private String password;

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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }


    @Override
    public String getUsername() {
        return this.name;
    }
}
