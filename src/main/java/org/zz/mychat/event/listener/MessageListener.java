package org.zz.mychat.event.listener;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.zz.mychat.event.MessageEvent;

@Slf4j
@Component
@Async
public class MessageListener implements ApplicationListener<MessageEvent> {


    @Override
    public void onApplicationEvent(MessageEvent event) {
        String message = event.getMsg();
        // 在这里执行处理用户登录事件的逻辑，例如记录日志或触发其他操作
        log.info("MessageListener handleUMessageEvent: {}, thread name: {}", message, Thread.currentThread().getName());
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("MessageListener interrupted: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("MessageListener error: {}", e.getMessage(), e);
        }
        log.info("MessageListener handle end, thread name: {}", Thread.currentThread().getName());

    }
}
