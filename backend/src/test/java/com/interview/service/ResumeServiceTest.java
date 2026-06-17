package com.interview.service;

import com.interview.entity.ResumeProfile;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.ResumeProfileMapper;
import com.interview.service.impl.ResumeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResumeService — 岗位隔离简历画像持久化")
class ResumeServiceTest {

    @Mock
    private ResumeProfileMapper resumeProfileMapper;

    @Mock
    private InterviewPositionMapper positionMapper;

    private ResumeServiceImpl resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeServiceImpl();
        resumeService.setResumeProfileMapper(resumeProfileMapper);
        ReflectionTestUtils.setField(resumeService, "positionMapper", positionMapper);
    }

    @Test
    @DisplayName("用户在该岗位无现有画像时执行 INSERT")
    void shouldInsertWhenNoExistingProfile() {
        when(resumeProfileMapper.selectOne(any())).thenReturn(null);

        resumeService.saveOrUpdateProfile(1L, 10L, "Java 后端", "{\"matchScore\":90}");

        ArgumentCaptor<ResumeProfile> captor = ArgumentCaptor.forClass(ResumeProfile.class);
        verify(resumeProfileMapper).insert(captor.capture());
        ResumeProfile inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(1L);
        assertThat(inserted.getPositionId()).isEqualTo(10L);
        assertThat(inserted.getPosition()).isEqualTo("Java 后端");
    }

    @Test
    @DisplayName("用户在该岗位已有画像时执行 UPDATE（同岗位覆盖）")
    void shouldUpdateWhenExistingProfileFound() {
        ResumeProfile existing = new ResumeProfile();
        existing.setId(100L);
        existing.setUserId(1L);
        existing.setPositionId(10L);
        when(resumeProfileMapper.selectOne(any())).thenReturn(existing);

        resumeService.saveOrUpdateProfile(1L, 10L, "前端", "{\"matchScore\":85}");

        ArgumentCaptor<ResumeProfile> captor = ArgumentCaptor.forClass(ResumeProfile.class);
        verify(resumeProfileMapper).updateById(captor.capture());
        ResumeProfile updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(100L);
        assertThat(updated.getPosition()).isEqualTo("前端");
    }

    @Test
    @DisplayName("不同岗位的画像相互隔离")
    void shouldIsolateProfilesByPosition() {
        ResumeProfile existing = new ResumeProfile();
        existing.setId(100L);
        existing.setUserId(1L);
        existing.setPositionId(10L);
        // positionId=10 已有画像，positionId=20 没有 → INSERT
        when(resumeProfileMapper.selectOne(any())).thenReturn(null);

        resumeService.saveOrUpdateProfile(1L, 20L, "AI 大模型", "{\"matchScore\":70}");

        verify(resumeProfileMapper).insert(any(ResumeProfile.class));
    }

    @Test
    @DisplayName("根据 userId + positionId 查询画像")
    void shouldReturnParsedProfileByUserAndPosition() {
        ResumeProfile profile = new ResumeProfile();
        profile.setUserId(1L);
        profile.setPositionId(10L);
        profile.setAnalysisJson("{\"matchScore\":80}");
        when(resumeProfileMapper.selectOne(any())).thenReturn(profile);

        var result = resumeService.getProfileByUserIdAndPosition(1L, 10L);

        assertThat(result).isNotNull();
        assertThat(result.getPositionId()).isEqualTo(10L);
        assertThat(result.getAnalysis()).isNotNull();
    }

    @Test
    @DisplayName("无画像时查询返回 null")
    void shouldReturnNullWhenNoProfile() {
        when(resumeProfileMapper.selectOne(any())).thenReturn(null);
        assertThat(resumeService.getProfileByUserIdAndPosition(99L, 999L)).isNull();
    }

    @Test
    @DisplayName("根据 userId + positionId 删除画像")
    void shouldDeleteProfileByUserAndPosition() {
        resumeService.deleteProfileByUserIdAndPosition(1L, 10L);
        verify(resumeProfileMapper).delete(any());
    }
}
