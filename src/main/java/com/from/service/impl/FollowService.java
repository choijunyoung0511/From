package com.from.service.impl;

import com.from.dto.FollowCountDto;
import com.from.dto.FollowToggleDto;
import com.from.repository.FollowRepository;
import com.from.repository.UserInfoRepository;
import com.from.repository.entity.FollowEntity;
import com.from.service.IFollowService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Slf4j //로그
@RequiredArgsConstructor // 생성자 자동주입 final붙은것들 즉 final로 선언된 Repository들의 생성자를 자동으로 만들어서 생성자 주입을 받을수 있게 한다
@Service //해당 로직이 비즈니스 당담이라는것으 알려주고 Bean으로 등록해라


//FollowerService 클래스 선언하면서 인터페이스를 구현하겠다고 지정한 코드
public class FollowService implements IFollowService {


    //팔로우 데이터에 접근하기 위해 FollowRepository를 서비스에 주입 받음
    private final FollowRepository followRepository;
    //팔로우 대상자가 실제로 존재하는지 확인하기 위해  UserInFoRepository를 서비스에 주입 받음
    private final UserInfoRepository userInfoRepository;

    // 팔로우 토글: 이미 팔로우 중이면 삭제(취소), 아니면 새로 저장
    // 처리 후 following 여부 + 최신 팔로워 수를 함께 반환 (프론트에서 버튼/카운트 즉시 갱신용)
    @Override

    //toggleFollow로직임 타입결과는 DTO로,매개변수2개는 followerId: 팔로우를 하는사람,follwoingId:팔로우를 당하는 사람
    public FollowToggleDto toggleFollow(String followerId, String followingId) {
        log.info("{}.toggleFollow Start!", this.getClass().getName());

        if (followerId.equals(followingId)) {
            //자기 자신 팔로우 불가 조건문으로 확인,컨트롤러에서 봤던 e.getMessage가 여기서 넣은 것을 가져오는거임
            throw new IllegalArgumentException("자기 자신은 팔로우할 수 없습니다.");
        }
        if (userInfoRepository.findByUserId(followingId).isEmpty()) {
            //팔로우 하려는 상대방이 실제로 존재하는 사용자인지 DB에서 확인하는 조건문임
            throw new IllegalArgumentException("존재하지 않는 사용자입니다.");
        }

        //핵심로직
        //팔로우 관계가 이미 존재하는지 조회하고 그 결과를 existing에 저장한다.
        //Optional은 값이 있을수도 있고 없을수도 있는 상자임
        Optional<FollowEntity> existing = followRepository.findByFollowerIdAndFollowingId(followerId, followingId);

        //boolean은 2가지 기능만 함 true는 현재 팔로우중, false는 현재 팔로우 중이 아님
        boolean following;
        //현재 팔로우 중이면 팔로우 목록에서 지워라
        if (existing.isPresent()) {
            followRepository.delete(existing.get());
            following = false;
        } else {
            //팔로우중이 아닐경우에는 저장해라
            followRepository.save(FollowEntity.builder().followerId(followerId).followingId(followingId).build());
            following = true;
        }

        log.info("{}.toggleFollow End!", this.getClass().getName());
        //팔로우 토글 결과 반환,팔로우/언팔로우 결과 + 상대방의 최신 팔로워 수를 반환한다
        return new FollowToggleDto(following, followRepository.countByFollowingId(followingId));
    }

    @Override
    //특정 사용자의 팔로잉 수와 팔로워 수를 둘다 조회하는 메서드임
    public FollowCountDto getFollowCounts(String userId) {

        //나를 팔로우 하는사람들의 수를 조회
        long followerCount = followRepository.countByFollowingId(userId);

        //내가 팔로우 하는사람들의 수
        long followingCount = followRepository.countByFollowerId(userId);
        //두 숫자를 DTO 하나에 담아서 Controller에 반환
        return new FollowCountDto(followerCount, followingCount);
    }

    @Override
    //현재 팔로우 하고 있는지 확인하는 메서드 상태 확인 메서드
    public boolean isFollowing(String followerId, String followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }
}
