// BoardController.java - 독서 게시판(공지사항형) 게시글 CRUD를 처리하는 컨트롤러
package com.from.controller;

import com.from.dto.BoardDto;
import com.from.service.IBoardService;
import com.from.util.CmmUtil;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {

    private final IBoardService boardService;

    // [GET] /board - 게시글 목록
    @GetMapping("")
    public String list(Model model) {
        log.info("{}.list Start!", this.getClass().getName());
        List<BoardDto> boards = boardService.findAll();
        model.addAttribute("boards", boards);
        log.info("{}.list End!", this.getClass().getName());
        return "board/list";
    }

    // [GET] /board/{id} - 게시글 상세
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        log.info("{}.detail Start!", this.getClass().getName());

        Optional<BoardDto> board = boardService.findById(id);
        if (board.isEmpty()) {
            return "redirect:/board"; //존재하지 않는 게시글이면 목록으로 이동
        }
        //글이 존재하면 isEmpty()는 false if 안으로 들어가지 않음 if 다음 코드 실행



        // 세션에 로그인한 사용자 ID 확인,isOwner는 이글의 작성자 본인인지 확인
        String sessionUserId = (String) session.getAttribute("SS_USER_ID");
        boolean isOwner = sessionUserId != null && sessionUserId.equals(board.get().userId());


        //model은 html 화면을 만들떄 사용할 데이터를담는 객체
        model.addAttribute("board", board.get());
        model.addAttribute("isOwner", isOwner);

        log.info("{}.detail End!", this.getClass().getName());
        return "board/detail";
    }

    // [GET] /board/write - 게시글 작성 화면
    @GetMapping("/write")
    public String writeForm(HttpSession session) {
        log.info("{}.writeForm Start!", this.getClass().getName());
        if (session.getAttribute("SS_USER_ID") == null) return "redirect:/user/login";
        log.info("{}.writeForm End!", this.getClass().getName());
        return "board/write";
    }

    // [POST] /board/write - 게시글 저장
    @PostMapping("/write")
    public String write(@RequestParam String title,
                         @RequestParam String content,
                         HttpSession session) {
        log.info("{}.write Start!", this.getClass().getName());


        //작성자 아이디는 입력칸에서 받지 않고 로그인 세션에서 가져옴
        String userId = (String) session.getAttribute("SS_USER_ID");

        //아이디가 널이면 로그인 화면으로 리턴
        if (userId == null) return "redirect:/user/login";


        //제목이나 내용 중 하나라도 비어있으면 저장하지않고 작성페이지로 이동
        String safeTitle = CmmUtil.nvl(title);
        String safeContent = CmmUtil.nvl(content);
        if (safeTitle.isEmpty() || safeContent.isEmpty()) {
            return "redirect:/board/write";
        }
        //서비스에게 저장 코드 넘김
        Long id = boardService.save(userId, safeTitle, safeContent);

        log.info("{}.write End!", this.getClass().getName());
        return "redirect:/board/" + id;
    }

    // [GET] /board/{id}/edit - 게시글 수정 화면 (작성자 본인만)
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model) {
        log.info("{}.editForm Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return "redirect:/user/login";

        Optional<BoardDto> board = boardService.findById(id);
        if (board.isEmpty()) return "redirect:/board";
        if (!board.get().userId().equals(userId)) return "redirect:/board/" + id; //본인 글이 아니면 상세로 이동

        model.addAttribute("board", board.get());

        log.info("{}.editForm End!", this.getClass().getName());
        return "board/edit";
    }

    // [POST] /board/{id}/edit - 게시글 수정 처리 (작성자 본인만)
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                        @RequestParam String title,
                        @RequestParam String content,
                        HttpSession session) {
        log.info("{}.edit Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return "redirect:/user/login";

        String safeTitle = CmmUtil.nvl(title);
        String safeContent = CmmUtil.nvl(content);
        if (safeTitle.isEmpty() || safeContent.isEmpty()) {
            return "redirect:/board/" + id + "/edit";
        }

        boardService.update(id, userId, safeTitle, safeContent);

        log.info("{}.edit End!", this.getClass().getName());
        return "redirect:/board/" + id;
    }

    // [POST] /board/{id}/delete - 게시글 삭제 처리 (작성자 본인만)
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session) {
        log.info("{}.delete Start!", this.getClass().getName());

        String userId = (String) session.getAttribute("SS_USER_ID");
        if (userId == null) return "redirect:/user/login";

        boardService.delete(id, userId);

        log.info("{}.delete End!", this.getClass().getName());
        return "redirect:/board";
    }
}
