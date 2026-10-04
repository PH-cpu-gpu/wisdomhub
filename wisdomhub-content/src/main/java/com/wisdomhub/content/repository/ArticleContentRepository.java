package com.wisdomhub.content.repository;

import com.wisdomhub.content.domain.document.ArticleContent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleContentRepository extends MongoRepository<ArticleContent, Long> {
}
