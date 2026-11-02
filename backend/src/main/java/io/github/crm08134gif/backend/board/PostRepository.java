package io.github.crm08134gif.backend.board;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {
    // 한 사건(또는 인물)의 글 목록. 최신순, cursor보다 작은 id만
    @Query("""
            select p from Post p
            join fetch p.author
            where p.board = :board and p.targetId = :targetId
              and p.deletedAt is null and p.id < :cursor
            order by p.id desc
            """)
    List<Post> findPage(BoardType board, String targetId, long cursor, Limit limit);

    // 드롭다운 "전체": 그 게시판의 모든 글
    @Query("""
            select p from Post p
            join fetch p.author
            where p.board = :board
              and p.deletedAt is null and p.id < :cursor
            order by p.id desc
            """)
    List<Post> findBoardPage(BoardType board, long cursor, Limit limit);

    // 글 본문 화면. 작성자 닉네임이 필요하므로 같이 읽는다
    @Query("select p from Post p join fetch p.author where p.id = :id")
    Optional<Post> findWithAuthor(long id);

    // 작성 횟수 제한: 이 회원이 after 이후에 쓴 글 수
    long countByAuthor_IdAndCreatedAtAfter(Long authorId, Instant after);
}
