package com.medical.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String username;
    private String displayName;
    private String role;
    private String message;
}
