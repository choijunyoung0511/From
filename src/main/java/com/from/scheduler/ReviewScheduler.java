package com.from.scheduler;

import com.from.config.EncryptUtil;
import com.from.domain.BookReviewDocument;
import com.from.domain.User;
import com.from.mapper.UserMapper;
import com.from.repository.BookReviewMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewScheduler {

    private final BookReviewMongoRepository bookReviewMongoRepository;
    private final UserMapper userMapper;
    private final JavaMailSender mailSender;

    @Scheduled(fixedRate = 60000)
    public void sendScheduledReviews() {
        // 발송 안 된 것 중 오늘 날짜 + 현재 시간 지난 것 조회
        List<BookReviewDocument> pending = bookReviewMongoRepository.findByIsSent(0);

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<BookReviewDocument> toSend = pending.stream()
                .filter(r -> r.getDeliveryDate() != null && r.getDeliveryTime() != null)
                .filter(r -> r.getDeliveryDate().isBefore(today) ||
                        (r.getDeliveryDate().isEqual(today) && !r.getDeliveryTime().isAfter(now)))
                .toList();

        if (toSend.isEmpty()) return;

        log.info("발송 대상 독후감: {}건", toSend.size());

        for (BookReviewDocument review : toSend) {
            try {
                User user = userMapper.findByUserId(review.getUserId());
                if (user == null) continue;

                String email = EncryptUtil.decryptAES(user.getEmail());

                if (email == null || email.isBlank()) {
                    log.warn("이메일 없음 (카카오 로그인) - id: {}, userId: {}", review.getId(), review.getUserId());
                    review.setIsSent(1);
                    bookReviewMongoRepository.save(review);
                    continue;
                }

                log.info("복호화된 이메일: {}", email);
                sendReviewMail(email, user.getName(), review);
                review.setIsSent(1);
                bookReviewMongoRepository.save(review);
                log.info("메일 발송 완료 - id: {}, to: {}", review.getId(), email);

            } catch (Exception e) {
                log.error("메일 발송 실패 - id: {}", review.getId(), e);
            }
        }
    }

    private void sendReviewMail(String toEmail, String name, BookReviewDocument review) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom("edwerd0511@naver.com");
        helper.setTo(toEmail);
        helper.setSubject("📬 FROM | 미래의 " + name + "님께 편지가 도착했어요!");
        helper.setText(buildHtmlContent(name, review), true);
        mailSender.send(message);
    }

    private String buildHtmlContent(String name, BookReviewDocument review) {
        int paperId = review.getPaperId() != null ? review.getPaperId() : 1;

        String bg = switch (paperId) {
            case 1  -> "linear-gradient(135deg, #FFF8F0 0%, #F5E6D3 100%)";
            case 2  -> "linear-gradient(135deg, #FFE4E8 0%, #FFBFCC 100%)";
            case 3  -> "linear-gradient(135deg, #1E3A5F 0%, #2E5A8F 100%)";
            case 4  -> "linear-gradient(135deg, #E0F5F0 0%, #B8EAE0 100%)";
            case 5  -> "linear-gradient(135deg, #F5F0E8 0%, #E8DCC8 100%)";
            case 6  -> "linear-gradient(135deg, #FFD6E7 0%, #FFAFD2 50%, #FFC8A2 100%)";
            case 7  -> "linear-gradient(135deg, #E8F4FD 0%, #AED6F1 50%, #85C1E9 100%)";
            case 8  -> "linear-gradient(135deg, #FDEBD0 0%, #F0B27A 50%, #E59866 100%)";
            case 9  -> "linear-gradient(135deg, #EAF2FF 0%, #D6E8FF 50%, #C3DEFF 100%)";
            case 10 -> "linear-gradient(135deg, #FFF0F5 0%, #FFD6E8 50%, #FFC2E0 100%)";
            case 11 -> "linear-gradient(135deg, #2C3E50 0%, #3D5166 50%, #4A6278 100%)";
            case 12 -> "linear-gradient(135deg, #F8F9FA 0%, #E8F5E9 50%, #C8E6C9 100%)";
            default -> "linear-gradient(135deg, #FFF8F0 0%, #F5E6D3 100%)";
        };

        String textColor = switch (paperId) {
            case 3, 11 -> "#ffffff";
            case 8     -> "#5D2E0C";
            default    -> "#333333";
        };

        String borderColor = switch (paperId) {
            case 1  -> "#D4956A";
            case 2  -> "#FF8FAB";
            case 3  -> "rgba(255,255,255,0.3)";
            case 4  -> "#7EC8C8";
            case 5  -> "#C4A882";
            case 6  -> "#FF85A2";
            case 7  -> "#5DADE2";
            case 8  -> "#CA6F1E";
            case 9  -> "#85A8D0";
            case 10 -> "#FF69B4";
            case 11 -> "rgba(255,255,255,0.2)";
            case 12 -> "#81C784";
            default -> "#D4956A";
        };

        String emoji = switch (paperId) {
            case 1  -> "🕯️";
            case 2  -> "🌸";
            case 3  -> "🌙";
            case 4  -> "🌿";
            case 5  -> "📜";
            case 6  -> "🌷";
            case 7  -> "🌊";
            case 8  -> "🍂";
            case 9  -> "❄️";
            case 10 -> "💕";
            case 11 -> "🌌";
            case 12 -> "🌱";
            default -> "✉️";
        };

        String labelColor = (paperId == 3 || paperId == 11) ? "rgba(255,255,255,0.6)" : "#888888";

        return """
            <div style="max-width:560px; margin:0 auto; font-family:'Nanum Myeongjo',Georgia,serif;">
                <div style="background:#6071E3; padding:24px; text-align:center; border-radius:12px 12px 0 0;">
                    <h1 style="color:white; margin:0; font-size:26px; font-style:italic; letter-spacing:2px;">from</h1>
                    <p style="color:#c5cbf7; margin:6px 0 0; font-size:13px;">미래의 나에게 보내는 독서 편지</p>
                </div>
                <div style="background:%s; padding:40px 36px; border-left:4px solid %s; line-height:2.2; color:%s; font-size:15px;">
                    <div style="text-align:right; font-size:13px; color:%s; margin-bottom:20px;">%s %s님께</div>
                    %s
                </div>
                <p style="text-align:center; color:#999; font-size:12px; margin-top:16px; padding-bottom:8px;">
                    FROM | 책을 읽고 미래의 나에게 편지를 보내세요 📚
                </p>
            </div>
            """.formatted(
                bg, borderColor, textColor,
                labelColor, emoji, name,
                review.getAiContent().replace("\n", "<br>")
        );
    }
}