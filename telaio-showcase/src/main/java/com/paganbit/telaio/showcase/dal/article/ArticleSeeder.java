package com.paganbit.telaio.showcase.dal.article;

import com.paganbit.telaio.showcase.seed.AbstractDemoSeeder;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Demo articles: one per {@link ArticleStatus}, so the role-based implicit filter of
 * {@code ArticleDalService} has something to hide from plain users.
 */
@Component
class ArticleSeeder extends AbstractDemoSeeder {

    private final ArticleRepository repository;

    ArticleSeeder(ArticleRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    protected void populate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        repository.save(article("Upcoming Features in Telaio 2.0", "upcoming-features-telaio-2",
            "We are working on exciting new features including GraphQL support and reactive streams...",
            "roadmap", ArticleStatus.DRAFT, null, "developer@example.com", 5));
        repository.save(article("Getting Started with Telaio", "getting-started-telaio",
            "Telaio is a Spring Boot framework that provides a unified Data Access Layer abstraction...",
            "tutorial", ArticleStatus.PUBLISHED, now.minusDays(10), "admin@example.com", 3));
        repository.save(article("Security in Telaio: Roles and RBAC", "security-telaio-roles-rbac",
            "Telaio provides a layered security model with operation-level authorization and field-level RBAC...",
            "security", ArticleStatus.PUBLISHED, now.minusDays(5), "admin@example.com", 2));
        repository.save(article("Telaio 1.0 Release Notes", "telaio-1-0-release-notes",
            "Telaio 1.0 introduced the core DAL abstraction with JPA support...",
            "release", ArticleStatus.ARCHIVED, now.minusMonths(6), "developer@example.com", 1));
    }

    @SuppressWarnings({"squid:S107", "squid:S4449"})
    private static Article article(
        String title, String slug, String content, String category, ArticleStatus status,
        @Nullable LocalDateTime publishedAt, String authorEmail, int revisionCount
    ) {
        Article article = new Article();
        article.setTitle(title);
        article.setSlug(slug);
        article.setContent(content);
        article.setCategory(category);
        article.setStatus(status);
        article.setPublishedAt(publishedAt);
        article.setAuthorEmail(authorEmail);
        article.setRevisionCount(revisionCount);
        return article;
    }
}
