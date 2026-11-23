package org.zz.mychat.common.utils;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.zz.mychat.common.enums.ResultEnum;

@Data
@Schema(description = "响应返回数据对象")
public class ApiResult<T> {

    @Schema(
            title = "code",
            description = "响应码",
            format = "int32",
            example = "200",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Integer code;

    @Schema(
            title = "success",
            description = "是否成功",
            accessMode = Schema.AccessMode.READ_ONLY,
            example = "true or false",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private boolean success;

    @Schema(
            title = "msg",
            description = "响应信息",
            accessMode = Schema.AccessMode.READ_ONLY,
            example = "响应信息",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String msg;

    private T data;


    public static <T> ApiResult<T> success() {
        ApiResult<T> apiResult = new ApiResult<T>();
        apiResult.setCode(200);
        apiResult.setSuccess(true);
        apiResult.setData(null);
        return apiResult;
    }

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> apiResult = new ApiResult<T>();
        apiResult.setCode(200);
        apiResult.setSuccess(true);
        apiResult.setData(data);
        return apiResult;
    }

    public static <T> ApiResult<T> success(ResultEnum resultEnum) {
        ApiResult<T> apiResult = new ApiResult<T>();
        apiResult.setSuccess(true);
        apiResult.setCode(resultEnum.getCode());
        apiResult.setMsg(resultEnum.getMsg());
        return apiResult;
    }

    public static <T> ApiResult<T> fail(Integer code, String msg) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setSuccess(false);
        apiResult.setCode(code);
        apiResult.setMsg(msg);
        return apiResult;
    }

    public static <T> ApiResult<T> fail(String code, String msg, T data) {
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setSuccess(false);
        try {
            apiResult.setCode(code != null ? Integer.valueOf(code) : 500);
        } catch (NumberFormatException e) {
            apiResult.setCode(500);
        }
        apiResult.setMsg(msg);
        apiResult.setData(data);
        return apiResult;
    }

    public static <T> ApiResult<T> fail(ResultEnum resultEnum) {
        ApiResult<T> apiResult = new ApiResult<T>();
        apiResult.setSuccess(false);
        apiResult.setCode(resultEnum.getCode());
        apiResult.setMsg(resultEnum.getMsg());
        return apiResult;
    }

}
