package com.interview.service;

import com.interview.entity.ResumeProfile;
import com.interview.mapper.ResumeProfileMapper;
import com.interview.service.impl.ResumeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResumeService — 简历画像持久化")
class ResumeServiceTest {

    @Mock
    private ResumeProfileMapper resumeProfileMapper;

    private ResumeServiceImpl resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeServiceImpl();
        resumeService.setResumeProfileMapper(resumeProfileMapper);
    }

    @Test
    @DisplayName("用户无现有画像时执行 INSERT")
    void shouldInsertWhenNoExistingProfile() {
        when(resumeProfileMapper.selectOne(any())).thenReturn(null);

        resumeService.saveOrUpdateProfile(1L, "Java 后端", "{\"matchScore\":90}");

        ArgumentCaptor<ResumeProfile> captor = ArgumentCaptor.forClass(ResumeProfile.class);
        verify(resumeProfileMapper).insert(captor.capture());
        ResumeProfile inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(1L);
        assertThat(inserted.getPosition()).isEqualTo("Java 后端");
    }

    @Test
    @DisplayName("用户已有画像时执行 UPDATE")
    void shouldUpdateWhenExistingProfileFound() {
        ResumeProfile existing = new ResumeProfile();
        existing.setId(100L);
        existing.setUserId(1L);
        when(resumeProfileMapper.selectOne(any())).thenReturn(existing);

        resumeService.saveOrUpdateProfile(1L, "前端", "{\"matchScore\":85}");

        ArgumentCaptor<ResumeProfile> captor = ArgumentCaptor.forClass(ResumeProfile.class);
        verify(resumeProfileMapper).updateById(captor.capture());
        ResumeProfile updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(100L);
        assertThat(updated.getPosition()).isEqualTo("前端");
    }

    @Test
    @DisplayName("根据 userId 查询画像返回解析后的 Map")
    void shouldReturnParsedProfileByUserId() {
        ResumeProfile profile = new ResumeProfile();
        profile.setUserId(1L);
        profile.setAnalysisJson("{\"matchScore\":80}");
        when(resumeProfileMapper.selectOne(any())).thenReturn(profile);

        Object result = resumeService.getProfileByUserId(1L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("无画像时查询返回 null")
    void shouldReturnNullWhenNoProfile() {
        when(resumeProfileMapper.selectOne(any())).thenReturn(null);
        assertThat(resumeService.getProfileByUserId(99L)).isNull();
    }
}
