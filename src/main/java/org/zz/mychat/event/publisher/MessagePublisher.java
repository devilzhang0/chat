package org.zz.mychat.event.publisher;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.zz.mychat.event.MessageEvent;

@Service
public class MessagePublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public MessagePublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishLoginEvent(String username) {
        MessageEvent messageEvent = new MessageEvent(this, username);
        applicationEventPublisher.publishEvent(messageEvent);
    }
}
