package com.interview.service.questionbank;

import com.interview.entity.KnowledgeAtom;

import java.time.LocalDateTime;

public record KnowledgeAtomResponse(
        Long id,
        String atomId,
        String subject,
        String category,
        String difficulty,
        String tagsJson,
        String principles,
        String pitfalls,
        String followUpPathsJson,
        String status,
        String vectorStatus,
        String reviewStatus,
        String reviewReason,
        Double reviewConfidence,
        String suggestedPatchJson,
        String publicationStatus,
        Integer currentVersionNo,
        LocalDateTime publishedAt,
        LocalDateTime updateTime
) {
    public static KnowledgeAtomResponse from(KnowledgeAtom atom) {
        return new KnowledgeAtomResponse(
                atom.getId(),
                atom.getAtomId(),
                atom.getSubject(),
                atom.getCategory(),
                atom.getDifficulty(),
                atom.getTagsJson(),
                atom.getPrinciples(),
                atom.getPitfalls(),
                atom.getFollowUpPathsJson(),
                atom.getStatus(),
                atom.getVectorStatus(),
                atom.getReviewStatus(),
                atom.getReviewReason(),
                atom.getReviewConfidence(),
                atom.getSuggestedPatchJson(),
                atom.getPublicationStatus(),
                atom.getCurrentVersionNo(),
                atom.getPublishedAt(),
                atom.getUpdateTime()
        );
    }
}
