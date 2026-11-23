package org.zz.mychat.event.listener;


import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.zz.mychat.event.MessageEvent;

@Component
@Async
public class MessageListener implements ApplicationListener<MessageEvent> {


    @Override
    public void onApplicationEvent(MessageEvent event) {
        String message = event.getMsg();
        // 在这里执行处理用户登录事件的逻辑，例如记录日志或触发其他操作
        System.out.println("MessageListener handleUMessageEvent: " + message + "thread name: " + Thread.currentThread().getName());
        try {
            Thread.sleep(10000);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        System.out.println("MessageListener handle end " + "thread name: " + Thread.currentThread().getName());

    }
}
