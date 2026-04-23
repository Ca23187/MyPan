package com.mypan.common.response;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum {
    OK(200, "Request successful"),
    NOT_FOUND(404, "Request address not found"),
    BAD_REQUEST(600, "Invalid request parameters"),
    ALREADY_EXISTS(601, "Information already exists"),
    EMAIL_ALREADY_EXISTS(601, "Email already exists"),
    NICKNAME_ALREADY_EXISTS(601, "Nickname already exists"),
    INTERNAL_ERROR(500, "Server error, please contact the administrator"),

    LOGIN_TIMEOUT(901, "Login timeout, please log in again"),
    NOT_LOGGED_IN(901, "Not logged in, please log in first"),
    TOKEN_INVALID(901, "Invalid token"),

    SHARE_NOT_FOUND(902, "Share link not found"),
    SHARE_DELETED(903, "Shared file has been deleted"),
    SHARE_OWNER_BANNED(904, "Share owner has been banned"),
    SHARE_EXPIRED(905, "Share has expired"),

    STORAGE_INSUFFICIENT(906, "Insufficient storage space, please upgrade"),
    NO_PERMISSION(907, "No permission to access"),
    FILE_NOT_FOUND(908, "File not found"),
    FILE_NOT_READABLE(908, "File is not readable"),

    MPU_SESSION_MISSING(909, "Multipart upload session missing"),
    MPU_PARTS_INCOMPLETE(910, "Multipart parts incomplete");


    private final Integer code;
    private final String msg;
}