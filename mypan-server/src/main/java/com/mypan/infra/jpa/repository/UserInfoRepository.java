package com.mypan.infra.jpa.repository;

import com.mypan.infra.jpa.entity.UserInfo;
import com.mypan.service.dto.UserSpaceDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserInfoRepository extends JpaRepository<UserInfo, String> {
    UserInfo findByEmail(String email);
    boolean existsByEmail(String email);
    UserInfo findByEmailOrNickname(String email, String nickname);

    @Query("update UserInfo u set u.qqAvatar = :qqAvatar where u.userId = :userId")
    @Modifying
    void updateQqAvatarByUserId(String qqAvatar, String userId);

    UserInfo findByQqOpenId(String qqOpenId);

    @Modifying
    @Query(value = """
    UPDATE user_info
    SET used_space =
      CASE
        WHEN used_space + :delta < 0 THEN 0
        ELSE used_space + :delta
      END
    WHERE user_id = :userId
      AND (:delta < 0 OR used_space + :delta <= total_space)
    """, nativeQuery = true)
    int addUsedSpace(String userId, Long delta);

    @Modifying
    @Query(value = """
    UPDATE user_info
    SET total_space = total_space + :delta
    WHERE user_id = :userId
      AND total_space + :delta >= used_space
      AND total_space + :delta >= 0
    """, nativeQuery = true)
    int addTotalSpace(String userId, Long delta);

    @Query("update UserInfo u set u.status = :status where u.userId = :userId")
    @Modifying
    void updateStatusByUserId(Integer status, String userId);

    @Query("update UserInfo u set u.usedSpace = :usedSpace where u.userId = :userId")
    @Modifying
    void updateUsedSpaceByUserId(Long usedSpace, String userId);

    @Query("""
        select new com.mypan.service.dto.UserSpaceDto(u.usedSpace, u.totalSpace)
        from UserInfo u
        where u.userId = :userId
    """)
    UserSpaceDto findUserSpaceDtoByUserId(String userId);
}
