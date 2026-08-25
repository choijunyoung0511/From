// FollowController.java - 팔로우 관련 HTTP 요청을 처리하는 컨트롤러
package com.from.controller;

import com.from.dto.FollowCountDto;
import com.from.dto.FollowToggleDto;
import com.from.dto.MsgDto; //에러 메시지 DTO
import com.from.service.IFollowService; //인터페이스

import jakarta.servlet.http.HttpSession; //로그인한 사용자의 세션 가져옴
import lombok.RequiredArgsConstructor; //생성자를 자동으로 만들어줌
import lombok.extern.slf4j.Slf4j; //로그

import org.springframework.http.ResponseEntity; //HTTP상태 코드와 응답데이터를 함께 반환할수 있게 해주는 클래스
import org.springframework.stereotype.Controller; //컨트롤러, Spring이 관리하는 객체(빈)으로 등록
import org.springframework.web.bind.annotation.GetMapping; // get방식으로 요청했을떄 어떤 메서드가 그 요청을 처리할지 연결 (데이터 조회)
import org.springframework.web.bind.annotation.PathVariable; //Url 경로에 포함된 값을 Controller의 매개변수로 가져오기 위해 사용
import org.springframework.web.bind.annotation.PostMapping; //Post 요청의 Url과 Controller 메서드를 연결(데이터에 변화를 발생시키는 요청)
import org.springframework.web.bind.annotation.ResponseBody; //메서드의 반환값을 http 응답 데이터로 직접 보내기 위해 사용

@Slf4j
@RequiredArgsConstructor
@Controller
public class FollowController {
    //final필드의 생성자를 자동 생성해서 의존성 주입을 편하게 하기위해서 사용

    //final 다음 타입 적은다음 Service실제코드
    private final IFollowService followService;


    // [POST] /user/{targetUserId}/follow - 팔로우 토글
    // 이미 팔로우 중 → 취소, 팔로우 안 함 → 추가


    //요청한 사람은 세션에서 가져오고,대상 사람은 url로 가져옴
    @PostMapping("/user/{targetUserId}/follow")
    @ResponseBody //메서드가 반환하는 값을 http 응답 데이터를  본문에 직접 넣어주기 위해 사용함
    // 성공과 실패시의 DTO가 다르기 떄문에 <?>사용
    //toggleFollow는 메서드 이름이며 현재 팔로우 상태에 따라 팔로우 또는 팔로우 취소를 처리함
    public ResponseEntity<?> toggleFollow(
            @PathVariable String targetUserId, //팔로우 대상 사용자의ID(팔로우 당하는 사람의 id)를 targetUserId 변수로 받아오는 부분임
            HttpSession session) {

        log.info("{}.toggleFollow Start!", this.getClass().getName());

        //세션에 저장되어 있는 로그인한 사용자의 id를 꺼내서 userId변수에 저장하는 코드임
        String userId = (String) session.getAttribute("SS_USER_ID");

        if (userId == null) {
            return ResponseEntity.status(401).body(
                    //로그인 정보가 없으므로 http401과 오류 내용을 클라이언트에게 반환
                    //builder패턴으로 MsgDTO 객체 생성함, 로그인 하지 않은 사용자에게 보낼 실패 결과와 메시지를 DTO로 만든다.
                    MsgDto.builder().result(0).msg("로그인이 필요합니다.").build()
            );
        }

        try {
            //팔로우 처리 코드를 실행해보고 예외가 발생하면 뒤의 catch에서 처리하기위해 시작하는 블록


            //Service에 실제 팔로우/언팔로우 처리를 맡기고, 그 처리 결과를 result에 저장 200
            // 한번 누를떄마다 팔로우 / 언팔로우 바뀜 토글 구조
            FollowToggleDto result = followService.toggleFollow(userId, targetUserId);
            log.info("{}.toggleFollow End!", this.getClass().getName());
            return ResponseEntity.ok(result);


        } catch (IllegalArgumentException e) {
            // 자기 자신 팔로우 시도, 존재하지 않는 사용자 등 잘못된 요청(특정 잘못된인자 예외를 잡을떄 사용)
            //catch로 예외 처리 잡음 자기자신 팔로우 했을떄
            //service에서 발생한 잘못된 요청의 이유를 warn로그로 기록 한다.
            //잘못된 팔로우 요청에 대해 Http 400 bad request반환
            log.warn("팔로우 요청 거부 - {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    MsgDto.builder().result(0).msg(e.getMessage()).build()
            );
        } catch (Exception e) {
            //예상치 못한 오류 발생시에 처리 로직,넓은 범위의 예외를 잡을떄 사용
            log.error("팔로우 처리 실패", e);
            return ResponseEntity.status(500).body(
                    MsgDto.builder().result(0).msg("팔로우 처리에 실패했습니다.").build()
            );
        }
    }

    // [GET] /user/{targetUserId}/followCounts - 팔로워/팔로잉 수 조회
    @GetMapping("/user/{targetUserId}/followCounts")
    //특정 사용자의 팔로워 수와 팔로잉 수를 조회하는 GET API
    @ResponseBody //팔로워 /팔로잉 조회 결과를 화면이 아니라 HTTP응답 데이터로 직접 반환함
    public ResponseEntity<?> getFollowCounts(@PathVariable String targetUserId) {
        //특정 사용자의 ID를 URL에 받아서 그 사용자의 팔로워/팔로잉 수를 조회하고 HTTP 응답으로 반환하는 메서드 선언

        log.info("{}.getFollowCounts Start!", this.getClass().getName());

        try {
            //서비스에게 특정 사용자의 팔로워 수와 팔로잉 수를 조회해달라고 요청하고, 그 결과를 result에 저장하는 코드
            FollowCountDto result = followService.getFollowCounts(targetUserId);
            log.info("{}.getFollowCounts End!", this.getClass().getName());
            // 로그 찍고 200 반환, 조회한 팔로우 수와 팔로잉 수 포함
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("팔로우 수 조회 실패", e);
            return ResponseEntity.status(500).body(
                    MsgDto.builder().result(0).msg("팔로우 정보를 불러오지 못했습니다.").build()
            );
        }
    }
}
