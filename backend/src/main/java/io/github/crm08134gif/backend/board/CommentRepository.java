package io.github.crm08134gif.backend.board;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
