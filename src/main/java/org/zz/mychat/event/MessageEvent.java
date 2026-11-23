package org.zz.mychat.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MessageEvent extends ApplicationEvent {

    private String msg;

    public MessageEvent(Object source, String msg) {
        super(source);
        this.msg = msg;
    }

}
