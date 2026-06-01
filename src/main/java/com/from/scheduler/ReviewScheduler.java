package com.from.scheduler;

import com.from.config.EncryptUtil;
import com.from.repository.document.BookReviewDocument;
import com.from.repository.BookReviewMongoRepository;
import com.from.repository.UserInfoRepository;
import com.from.repository.entity.UserInfoEntity;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;


//이메일 발송 스케줄러,1분마다 실행하여 발송시각이 지난 미발송 도큐먼트 처리
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewScheduler {

    private final BookReviewMongoRepository bookReviewMongoRepository;
    private final UserInfoRepository userInfoRepository;   // JPA 사용 (userId = String)
    private final JavaMailSender mailSender;


    // 발송 예약 시간이 도래한 항목을 찾아 이메일을 발송한다.
    @Scheduled(fixedRate = 60000)
    public void sendScheduledReviews() {
        log.debug("{}.sendScheduledReviews Start!", this.getClass().getName());

        // 아직 발송되지 않은 독후감 전체 조회
        List<BookReviewDocument> pending;
        try {
            pending = bookReviewMongoRepository.findByIsSent(0);
        } catch (Exception e) {
            log.warn("MongoDB 연결 실패 - 스케줄러 스킵: {}", e.getMessage());
            return;
        }

        LocalDate today = LocalDate.now();
        LocalTime now   = LocalTime.now();

        // 발송 날짜·시각이 도래한 항목만 필터링
        List<BookReviewDocument> toSend = pending.stream()
                .filter(r -> r.getDeliveryDate() != null && r.getDeliveryTime() != null)
                .filter(r -> r.getDeliveryDate().isBefore(today) ||
                        (r.getDeliveryDate().isEqual(today) && !r.getDeliveryTime().isAfter(now)))
                .toList();

        if (toSend.isEmpty()) {
            log.debug("{}.sendScheduledReviews End! - 발송 대상 없음", this.getClass().getName());
            return;
        }

        log.info("발송 대상 독후감: {}건", toSend.size());

        for (BookReviewDocument review : toSend) {
            try {
                // userId(String 로그인 ID) 로 JPA 에서 유저 정보 조회
                UserInfoEntity userEntity = userInfoRepository.findByUserId(review.getUserId())
                        .orElse(null);

                if (userEntity == null) {
                    log.warn("유저 없음 - userId: {}", review.getUserId());
                    continue;
                }

                // AES 복호화하여 실제 이메일 주소 획득
                String email = EncryptUtil.decryptAES(userEntity.getEmail());

                if (email == null || email.isBlank()) {
                    log.warn("이메일 없음 - id: {}", review.getId());
                    // 발송 불가 항목은 완료 처리하여 무한 재시도 방지
                    review.setIsSent(1);
                    bookReviewMongoRepository.save(review);
                    continue;
                }

                log.info("메일 발송 시작 - to: {}", email);
                sendReviewMail(email, userEntity.getName(), review);

                // 발송 완료 표시
                review.setIsSent(1);
                bookReviewMongoRepository.save(review);
                log.info("메일 발송 완료 - id: {}, to: {}", review.getId(), email);

            } catch (Exception e) {
                log.error("메일 발송 실패 - id: {}", review.getId(), e);
            }
        }

        log.info("{}.sendScheduledReviews End!", this.getClass().getName());
    }

    //메일 발송코드
    //수신자 이메일,이름,독후감
    private void sendReviewMail(String toEmail, String name, BookReviewDocument review) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom("edwerd0511@naver.com");
        helper.setTo(toEmail);
        helper.setSubject("📬 FROM | 미래의 " + name + "님께 편지가 도착했어요!");
        helper.setText(buildHtmlContent(name, review), true);
        mailSender.send(message);
    }

   //html반환 — result.html 의 letter-wrap / letter-date / letter-body 와 동일한 구조·색상
    private String buildHtmlContent(String name, BookReviewDocument review) {
        int paperId = review.getPaperId() != null ? review.getPaperId() : 1;

        // result.html paper-N 배경 그라데이션과 동일
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

        // result.html paper-N { color: ... } 과 정확히 동일
        String textColor = switch (paperId) {
            case 1  -> "#4A3B31";
            case 2  -> "#5A3D45";
            case 3  -> "#F8FBFF";
            case 4  -> "#355850";
            case 5  -> "#5B4A39";
            case 6  -> "#5B3E4A";
            case 7  -> "#214865";
            case 8  -> "#5D2E0C";
            case 9  -> "#35506D";
            case 10 -> "#5A3F4A";
            case 11 -> "#F7FAFD";
            case 12 -> "#35513A";
            default -> "#4A3B31";
        };

        // 편지지 테마별 왼쪽 테두리 색상
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

        // result.html letter-date 와 동일한 날짜 형식
        String today = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));

        // AI 생성 콘텐츠의 % 문자를 이스케이프 (String.format 오동작 방지)
        String safeContent = review.getAiContent()
                .replace("%", "%%")
                .replace("\n", "<br>");

        return ("""
            <div style="max-width:560px; margin:0 auto; font-family:Georgia,'Times New Roman',serif;">
                <div style="background:#6071E3; padding:24px; text-align:center; border-radius:12px 12px 0 0;">
                    <h1 style="color:white; margin:0; font-size:26px; font-style:italic; letter-spacing:2px;">from</h1>
                    <p style="color:#c5cbf7; margin:6px 0 0; font-size:13px;">미래의 나에게 보내는 독서 편지</p>
                </div>
                <div style="background:%s; padding:56px 42px; border-left:4px solid %s; color:%s; word-break:keep-all; border-radius:0 0 12px 12px;">
                    <p style="margin:0 0 28px; font-size:14px; font-weight:500; opacity:0.9;">%s</p>
                    <p style="margin:0; font-size:16px; line-height:2.05; letter-spacing:-0.01em;">%s</p>
                </div>
                <p style="text-align:center; color:#999; font-size:12px; margin-top:16px; padding-bottom:8px;">
                    FROM | 책을 읽고 미래의 나에게 편지를 보내세요
                </p>
            </div>
            """).formatted(bg, borderColor, textColor, today, safeContent);
    }
}
