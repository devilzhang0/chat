package org.zz.mychat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.zz.mychat.common.utils.ApiResult;
import org.zz.mychat.event.publisher.MessagePublisher;

@RestController
@Tag(name = "helloController api",
        description = "测试使用")
public class HelloController {

    @Autowired
    private MessagePublisher messagePublisher;

    @GetMapping("/hello")
    @Operation(summary = "测试hello", description = "ceshi hello")
    public ApiResult<String> hello() {
        return ApiResult.success("hello");
    }

    @GetMapping("test/{message}")
    public ApiResult<String> test(@PathVariable String message) {
        messagePublisher.publishLoginEvent(message);
        return ApiResult.success();
    }
}
