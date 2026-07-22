package com.medical.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        var r = new ApiResponse<T>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        var r = new ApiResponse<T>();
        r.code = code;
        r.message = message;
        return r;
    }
}
