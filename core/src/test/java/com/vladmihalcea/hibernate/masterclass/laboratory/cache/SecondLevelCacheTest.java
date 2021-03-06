package com.vladmihalcea.hibernate.masterclass.laboratory.cache;

import com.vladmihalcea.hibernate.masterclass.laboratory.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * SecondLevelCacheTest - Test to check the 2nd level cache
 *
 * @author Vlad Mihalcea
 */
public class SecondLevelCacheTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Post.class,
                PostDetails.class,
                Comment.class
        };
    }

    @Override
    protected Properties getProperties() {
        Properties properties = super.getProperties();
        properties.put("hibernate.cache.use_second_level_cache", Boolean.TRUE.toString());
        properties.put("hibernate.cache.use_query_cache", Boolean.TRUE.toString());
        properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory");
        return properties;
    }

    private Post post;

    @Before
    public void init() {
        super.init();
        doInTransaction(session -> {
            post = new Post();
            post.setName("Hibernate Master Class");

            post.addDetails(new PostDetails());
            post.addComment(new Comment("Good post!"));
            post.addComment(new Comment("Nice post!"));

            session.persist(post);
            session.get(PostDetails.class, post.getDetails().getId());
        });
    }

    @After
    public void destroy() {
        getSessionFactory().getCache().evictAllRegions();
        super.destroy();
    }

    @Test
    public void testGetPost() {
        doInTransaction(session -> {
            LOGGER.info("Check Post entity is cached after load");
            Post post = (Post) session.get(Post.class, 1L);
        });
        doInTransaction(session -> {
            LOGGER.info("Check Post entity is cached after load");
            Post post = (Post) session.get(Post.class, 1L);
        });
    }

    @Test
    public void testGetPostDetails() {
        doInTransaction(session -> {
            PostDetails postDetails = (PostDetails) session.get(PostDetails.class, 1L);
        });
        doInTransaction(session -> {
            LOGGER.info("Check PostDetails entity is cached after load");
            PostDetails postDetails = (PostDetails) session.get(PostDetails.class, 1L);
        });
    }

    @Test
    public void testGetComment() {
        doInTransaction(session -> {
            Comment comment = (Comment) session.get(Comment.class, 2L);
        });
        doInTransaction(session -> {
            LOGGER.info("Check Comment entity is cached after load");
            Comment comment = (Comment) session.get(Comment.class, 2L);
        });
    }

    @Test
    public void testGetCommentWithNewComment() {
        doInTransaction(session -> {
            Comment comment = (Comment) session.get(Comment.class, 2L);
        });
        LOGGER.info("Check Comment entity is cached even after new entity is added");
        doInTransaction(session -> {
            Post post = (Post) session.get(Post.class, 1L);
            Comment newComment = new Comment();
            newComment.setReview("Thanks for sharing.");
            post.addComment(newComment);
            session.flush();

            LOGGER.info("Check Comment entity is cached after load");
            Comment comment = (Comment) session.get(Comment.class, 2L);
        });
    }

    @Test
    public void test2ndLevelCacheWithQuery() {
        doInTransaction(session -> {

            Post post = (Post) session.createQuery(
                    "select p " +
                            "from Post p " +
                            "join fetch p.details " +
                            "join fetch p.comments " +
                            "where " +
                            "   p.id = :id").setParameter("id", 1L)
                    .setCacheable(true)
                    .uniqueResult();
        });
        doInTransaction(session -> {
            LOGGER.info("Check get entity is cached after query");
            Post post = (Post) session.get(Post.class, 1L);
        });

        doInTransaction(session -> {
            LOGGER.info("Check query entity is cached after query");
            Post post = (Post) session.createQuery(
                    "select p " +
                            "from Post p " +
                            "join fetch p.details " +
                            "join fetch p.comments " +
                            "where " +
                            "   p.id = :id").setParameter("id", 1L)
                    .setCacheable(true)
                    .uniqueResult();

            post.setName("High-Performance Hibernate");
            session.flush();

            LOGGER.info("Check query entity query is invalidated");
            post = (Post) session.createQuery(
                    "select p " +
                            "from Post p " +
                            "join fetch p.details " +
                            "where " +
                            "   p.id = :id").setParameter("id", 1L)
                    .setCacheable(true)
                    .uniqueResult();
        });
    }

    @Test
    public void test2ndLevelCacheWithQueryInvalidation() {
        doInTransaction(session -> {
            Post post = (Post) session.createQuery(
                    "select p " +
                            "from Post p " +
                            "join fetch p.details " +
                            "join fetch p.comments " +
                            "where " +
                            "   p.id = :id").setParameter("id", 1L)
                    .setCacheable(true)
                    .uniqueResult();
        });

        doInTransaction(session -> {
            LOGGER.info("Check query entity is cached after query");
            Post post = (Post) session.createQuery(
                    "select p " +
                            "from Post p " +
                            "join fetch p.details " +
                            "join fetch p.comments " +
                            "where " +
                            "   p.id = :id").setParameter("id", 1L)
                    .setCacheable(true)
                    .uniqueResult();

            LOGGER.info("Insert a new Post!");

            Post newPost = new Post();
            newPost.setName("Hibernate Book!");
            session.persist(newPost);
            session.flush();

            LOGGER.info("Check query entity query is invalidated");
            post = (Post) session.createQuery(
                    "select p " +
                            "from Post p " +
                            "join fetch p.details " +
                            "where " +
                            "   p.id = :id").setParameter("id", 1L)
                    .setCacheable(true)
                    .uniqueResult();
        });
    }

    @Entity(name = "Post")
    @Cacheable
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    public static class Post {

        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;

        private String name;

        @OneToMany(cascade = CascadeType.ALL, mappedBy = "post")
        private List<Comment> comments = new ArrayList<>();

        @OneToOne(cascade = CascadeType.ALL, mappedBy = "post", optional = true)
        private PostDetails details;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Comment> getComments() {
            return comments;
        }

        public PostDetails getDetails() {
            return details;
        }

        public void addComment(Comment comment) {
            comments.add(comment);
            comment.setPost(this);
        }

        public void addDetails(PostDetails details) {
            this.details = details;
            details.setPost(this);
        }
    }

    @Entity(name = "PostDetails")
    @Cacheable
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    public static class PostDetails {

        @Id
        private Long id;

        private Date createdOn;

        public PostDetails() {
            createdOn = new Date();
        }

        @OneToOne
        @JoinColumn(name = "id")
        @MapsId
        private Post post;

        public Long getId() {
            return id;
        }

        public void setPost(Post post) {
            this.post = post;
        }
    }

    @Entity(name = "Comment")
    @Cacheable
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    public static class Comment {

        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;

        @ManyToOne
        private Post post;

        public Comment() {
        }

        public Comment(String review) {
            this.review = review;
        }

        private String review;

        public Long getId() {
            return id;
        }

        public void setPost(Post post) {
            this.post = post;
        }

        public void setReview(String review) {
            this.review = review;
        }
    }
}
