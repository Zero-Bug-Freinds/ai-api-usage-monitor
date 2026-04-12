package com.zerobugfreinds.identity_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 비밀번호 재설정 링크 전달. {@code spring.mail.host} 가 없으면 SMTP 대신 로그로 링크를 남긴다(로컬 개발).
 */
@Service
public class PasswordResetMailService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

	private final JavaMailSender mailSender;
	private final String webBaseUrl;
	private final String mailFrom;
	private final int tokenValidityHours;
	private final String applicationName;

	public PasswordResetMailService(
			@Autowired(required = false) JavaMailSender mailSender,
			@Value("${identity.passwordReset.webBaseUrl}") String webBaseUrl,
			@Value("${identity.passwordReset.mailFrom}") String mailFrom,
			@Value("${identity.passwordReset.tokenValidityHours}") int tokenValidityHours,
			@Value("${spring.application.name:identity-service}") String applicationName
	) {
		this.mailSender = mailSender;
		this.webBaseUrl = webBaseUrl;
		this.mailFrom = mailFrom;
		this.tokenValidityHours = tokenValidityHours;
		this.applicationName = applicationName;
	}

	public void sendResetLink(String toEmail, String rawToken) {
		String base = webBaseUrl.replaceAll("/+$", "");
		String tokenParam = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
		String link = base + "/reset-password?token=" + tokenParam;

		String subject = "[" + applicationName + "] 비밀번호 재설정";
		String body = """
				비밀번호 재설정을 요청하셨습니다. 아래 링크를 클릭하여 새 비밀번호를 설정해 주세요.

				%s

				링크는 %d시간 동안 유효합니다. 본인이 요청하지 않았다면 이 메일을 무시해 주세요.
				""".formatted(link, tokenValidityHours);

		if (mailSender == null) {
			log.info("[password-reset] SMTP 미구성: email={} resetLink={}", toEmail, link);
			return;
		}

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(mailFrom);
		message.setTo(toEmail);
		message.setSubject(subject);
		message.setText(body);
		mailSender.send(message);
	}
}
