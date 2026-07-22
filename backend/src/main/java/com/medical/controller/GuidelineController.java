package com.medical.controller;

import com.medical.dto.ApiResponse;
import com.medical.entity.MedicalGuideline;
import com.medical.repository.MedicalGuidelineRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/guidelines")
public class GuidelineController {

    private final MedicalGuidelineRepository guidelineRepository;

    public GuidelineController(MedicalGuidelineRepository guidelineRepository) {
        this.guidelineRepository = guidelineRepository;
    }

    @GetMapping("/search")
    public ApiResponse<List<MedicalGuideline>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {

        List<MedicalGuideline> all;
        if (category != null && !category.isEmpty()) {
            all = guidelineRepository.findByCategory(category);
        } else {
            all = guidelineRepository.findAll();
        }

        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.toLowerCase();
            all = all.stream()
                    .filter(g -> g.getTitle().toLowerCase().contains(kw)
                            || g.getContent().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
        }

        return ApiResponse.success(all);
    }
}
