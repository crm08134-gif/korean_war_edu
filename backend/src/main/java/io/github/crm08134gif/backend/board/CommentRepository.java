package io.github.crm08134gif.backend.board;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;


public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 글 본문 아래 댓글. 오래된 것부터
    @Query("""
            select c from Comment c
            join fetch c.author
            where c.post.id = :postId and c.deletedAt is null
            order by c.id
            """)
    List<Comment> findByPost(long postId);

    long countByAuthor_IdAndCreatedAtAfter(Long authorId, Instant after);

    // 신고·관리자 처리 때 대상 행을 잠근다. 두 사람이 동시에 신고해도 한 줄로 서서 "2명"을 정확히 센다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comment c where c.id = :id")
    Optional<Comment> findForUpdate(long id);
}
