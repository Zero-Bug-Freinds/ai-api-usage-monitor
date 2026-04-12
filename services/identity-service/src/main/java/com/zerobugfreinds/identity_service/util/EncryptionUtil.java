package com.zerobugfreinds.identity_service.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 외부 API 키 평문을 AES-256-GCM으로 암호화하고, 중복 검사용 SHA-256 해시를 만든다.
 * 평문은 DB에 저장하지 않는다.
 */
@Component
public class EncryptionUtil {

	private static final int GCM_IV_LENGTH = 12;
	private static final int GCM_TAG_BITS = 128;
	private static final String AES_GCM = "AES/GCM/NoPadding";

	private final SecretKey aesKey;
	private final SecureRandom secureRandom = new SecureRandom();

	public EncryptionUtil(@Value("${identity.api-key.encryption.secret}") String encryptionSecret) {
		this.aesKey = deriveAes256Key(encryptionSecret);
	}

	/**
	 * 동일 키 재등록 방지용 SHA-256 해시(16진 문자열). provider·키 평문을 함께 넣어 구분한다.
	 */
	public String sha256HexForUniqueness(String providerName, String normalizedPlainKey) {
		String payload = providerName + "\0" + normalizedPlainKey;
		return sha256HexUtf8(payload);
	}

	/**
	 * 임의 문자열(UTF-8)에 대한 SHA-256 16진 문자열. 비밀번호 재설정 토큰 해시 등에 사용한다.
	 */
	public String sha256HexUtf8(String utf8Text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(utf8Text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

	/**
	 * AES-256-GCM으로 암호화한 뒤 Base64(IV + ciphertext) 문자열로 반환한다.
	 */
	public String encryptAes256Gcm(String plainText) {
		byte[] iv = new byte[GCM_IV_LENGTH];
		secureRandom.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
			buffer.put(iv);
			buffer.put(cipherBytes);
			return java.util.Base64.getEncoder().encodeToString(buffer.array());
		} catch (Exception e) {
			throw new IllegalStateException("외부 API 키 암호화에 실패했습니다", e);
		}
	}

	/**
	 * Base64(IV + ciphertext) 형식을 복호화해 평문을 반환한다.
	 */
	public String decryptAes256Gcm(String encrypted) {
		try {
			byte[] payload = java.util.Base64.getDecoder().decode(encrypted);
			if (payload.length <= GCM_IV_LENGTH) {
				throw new IllegalArgumentException("암호문 형식이 올바르지 않습니다");
			}
			ByteBuffer buffer = ByteBuffer.wrap(payload);
			byte[] iv = new byte[GCM_IV_LENGTH];
			buffer.get(iv);
			byte[] cipherBytes = new byte[buffer.remaining()];
			buffer.get(cipherBytes);

			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] plain = cipher.doFinal(cipherBytes);
			return new String(plain, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException("외부 API 키 복호화에 실패했습니다", e);
		}
	}

	private static SecretKey deriveAes256Key(String secret) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(keyBytes, "AES");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
