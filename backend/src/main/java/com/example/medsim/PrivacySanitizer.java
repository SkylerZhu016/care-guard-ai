package com.example.medsim;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
class PrivacySanitizer {
    private static final String MASK = "[已脱敏]";
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern CN_PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern CN_ID = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?i)(?<![\\w.-])[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}(?![\\w.-])");
    private static final Pattern LABELED_NAME = Pattern.compile("(姓名|真实姓名)\\s*[:：]?\\s*[\\p{IsHan}·]{2,20}");
    private static final Pattern ADDRESS = Pattern.compile("(住址|地址)\\s*[:：]?\\s*[^，。;；\\n]{4,80}");

    String sanitize(String value) {
        if (value == null) return "";
        String result = CONTROL.matcher(value).replaceAll("").strip();
        result = CN_PHONE.matcher(result).replaceAll(MASK);
        result = CN_ID.matcher(result).replaceAll(MASK);
        result = EMAIL.matcher(result).replaceAll(MASK);
        result = LABELED_NAME.matcher(result).replaceAll("$1：" + MASK);
        result = ADDRESS.matcher(result).replaceAll("$1：" + MASK);
        return result;
    }

    boolean containsIdentifier(String value) {
        if (value == null) return false;
        return CN_PHONE.matcher(value).find() || CN_ID.matcher(value).find() || EMAIL.matcher(value).find();
    }
}
