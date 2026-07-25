package top.myslzhao.mcbotchat.utils;

/**
 * Api-key 验证状态枚举
 */
public enum ApiKeyStatus {
    SUCCESS,                // 成功
    NETWORK_ERROR,          // 网络错误
    UNAUTHORIZED,           // 未授予
    INSUFFICIENT_BALANCE,   // 余额不足
    OTHER_ERROR             // 其他错误
}