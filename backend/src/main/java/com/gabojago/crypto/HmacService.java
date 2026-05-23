package com.tripmate.backend.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HMAC-SHA256 서비스.
 * 이메일 등 equality search가 필요한 필드의 중복 검사에 사용.
 */
@Service
public class HmacService {

    private final SecretKeySpec keySpec;

    public HmacService(@Value("${encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        this.keySpec = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    public String hmac(String value) {
        if (value == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] result = mac.doFinal(value.toLowerCase().trim().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("HMAC 생성 실패", e);
        }
    }
}
