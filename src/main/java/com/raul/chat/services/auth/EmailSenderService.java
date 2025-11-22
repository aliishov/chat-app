package com.raul.chat.services.auth;

import com.raul.chat.models.mail.EmailNotificationSubject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
@RequiredArgsConstructor
@EnableAsync
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailSenderService {

    final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    String from;

    @Async
    public void sendEmail(@NotNull String toEmail,
                          @NotNull EmailNotificationSubject subject,
                          @NotNull Map<String, String> placeholders) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            messageHelper.setTo(toEmail);
            messageHelper.setFrom(from);
            messageHelper.setSubject(subject.toString());

            String emailContent = fillTemplatePlaceholders(getEmailTemplate(subject), placeholders);
            messageHelper.setText(emailContent, true);

            mailSender.send(mimeMessage);
            log.info("Email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Error while sending email to {}", toEmail, e);
        }
    }

    private String getEmailTemplate(EmailNotificationSubject subject) {
        try {
            String fileName = "templates/" + subject.name() + ".html";
            Path path = new ClassPathResource(fileName).getFile().toPath();
            return Files.readString(path);
        } catch (IOException e) {
            log.error("Error loading email template for subject: {}", subject, e);
            throw new RuntimeException("Email template not found");
        }
    }

    private String fillTemplatePlaceholders(String template, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }
}
