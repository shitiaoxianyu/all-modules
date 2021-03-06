package com.earnest.web.repository;

import com.earnest.video.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VideoRepository extends MongoRepository<Video, String> {

    Page<Video> findByCategory(Pageable pageRequest, Video.Category category);

}
