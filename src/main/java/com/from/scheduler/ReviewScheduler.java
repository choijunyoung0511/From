package com.from.scheduler;

import com.from.config.EncryptUtil;
import com.from.domain.BookReview;
import com.from.domain.User;
import com.from.mapper.BookReviewMapper;
import com.from.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewScheduler {

    private final BookReviewMapper bookReviewMapper;
    private final UserMapper userMapper;
    private final JavaMailSender mailSender;

    // 매 1분마다 발송 대상 체크
    @Scheduled(fixedRate = 60000)
    public void sendScheduledReviews() {
        List<BookReview> pending = bookReviewMapper.findPendingReviews();

        if (pending.isEmpty()) return;

        log.info("발송 대상 독후감: {}건", pending.size());

        for (BookReview review : pending) {
            try {
                User user = userMapper.findByUserId(review.getUserId());
                if (user == null) continue;

                // 이메일 복호화
                String email = EncryptUtil.decryptAES(user.getEmail());
                log.info("복호화된 이메일: {}", email);

                sendReviewMail(email, user.getName(), review);
                bookReviewMapper.markAsSent(review.getReviewId());

                log.info("메일 발송 완료 - reviewId: {}, to: {}", review.getReviewId(), email);

            } catch (Exception e) {
                log.error("메일 발송 실패 - reviewId: {}", review.getReviewId(), e);
            }
        }
    }

    private void sendReviewMail(String toEmail, String name, BookReview review) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom("edwerd0511@naver.com");
        helper.setTo(toEmail);
        helper.setSubject("📬 FROM | 미래의 " + name + "님께 편지가 도착했어요!");
        helper.setText(buildHtmlContent(name, review), true);

        mailSender.send(message);
    }

    private String buildHtmlContent(String name, BookReview review) {
        String bg = switch (review.getPaperId()) {
            case 1 -> "linear-gradient(135deg, #FFF8F0, #F5E6D3)";
            case 2 -> "linear-gradient(135deg, #FFE4E8, #FFBFCC)";
            case 3 -> "linear-gradient(135deg, #1E3A5F, #2E5A8F)";
            case 4 -> "linear-gradient(135deg, #E0F5F0, #B8EAE0)";
            case 5 -> "linear-gradient(135deg, #F5F0E8, #E8DCC8)";
            default -> "linear-gradient(135deg, #FFF8F0, #F5E6D3)";
        };
        String textColor = review.getPaperId() == 3 ? "#ffffff" : "#333333";

        return """
            <div style="max-width:560px; margin:0 auto; font-family:'Nanum Myeongjo',serif;">
                <div style="background:#6071E3; padding:24px; text-align:center; border-radius:12px 12px 0 0;">
                    <h1 style="color:white; margin:0; font-size:24px;">from</h1>
                    <p style="color:#c5cbf7; margin:8px 0 0; font-size:13px;">미래의 나에게 보내는 독서 편지</p>
                </div>
                <div style="background:%s; padding:40px 36px; border-radius:0 0 12px 12px; line-height:2; color:%s; font-size:15px;">
                    %s
                </div>
                <p style="text-align:center; color:#999; font-size:12px; margin-top:16px;">
                    FROM | 책을 읽고 미래의 나에게 편지를 보내세요 📚
                </p>
            </div>
            """.formatted(bg, textColor, review.getAiContent().replace("\n", "<br>"));
    }
}