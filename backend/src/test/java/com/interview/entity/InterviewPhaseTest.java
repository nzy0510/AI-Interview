package com.interview.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewPhase 状态转换")
class InterviewPhaseTest {

    // Simulates the phase transition logic that will live in InterviewServiceImpl
    private InterviewPhase nextPhase(InterviewPhase current, int turn,
                                     boolean switchToHrMarker, boolean autoFinishMarker) {
        if (current == InterviewPhase.FINISHED) {
            return InterviewPhase.FINISHED;
        }
        if (autoFinishMarker) {
            return InterviewPhase.FINISHED;
        }
        if (current == InterviewPhase.OPENING && turn >= 1) {
            return InterviewPhase.TECHNICAL;
        }
        if (current == InterviewPhase.TECHNICAL) {
            if (switchToHrMarker || turn > 8) {
                return InterviewPhase.HR;
            }
            return InterviewPhase.TECHNICAL;
        }
        if (current == InterviewPhase.HR && turn > 11) {
            return InterviewPhase.CLOSING;
        }
        return current;
    }

    @Test
    @DisplayName("新面试从 OPENING 开始")
    void newInterviewStartsInOpening() {
        assertThat(nextPhase(InterviewPhase.OPENING, 0, false, false))
                .isEqualTo(InterviewPhase.OPENING);
    }

    @Test
    @DisplayName("OPENING 第 1 轮后进入 TECHNICAL")
    void openingAdvancesToTechnicalAfterFirstTurn() {
        assertThat(nextPhase(InterviewPhase.OPENING, 1, false, false))
                .isEqualTo(InterviewPhase.TECHNICAL);
    }

    @Test
    @DisplayName("TECHNICAL 检测到 [SWITCH_TO_HR] 标记时进入 HR")
    void technicalSwitchesToHrOnMarker() {
        assertThat(nextPhase(InterviewPhase.TECHNICAL, 3, true, false))
                .isEqualTo(InterviewPhase.HR);
    }

    @Test
    @DisplayName("TECHNICAL 超过 8 轮强制进入 HR（兜底）")
    void technicalFallsThroughToHrAfterMaxTurns() {
        assertThat(nextPhase(InterviewPhase.TECHNICAL, 9, false, false))
                .isEqualTo(InterviewPhase.HR);
    }

    @Test
    @DisplayName("HR 超过 11 轮进入 CLOSING")
    void hrAdvancesToClosingAfterThreshold() {
        assertThat(nextPhase(InterviewPhase.HR, 12, false, false))
                .isEqualTo(InterviewPhase.CLOSING);
    }

    @Test
    @DisplayName("CLOSING 检测到 [AUTO_FINISH] 标记时进入 FINISHED")
    void closingFinishesOnAutoFinishMarker() {
        assertThat(nextPhase(InterviewPhase.CLOSING, 13, false, true))
                .isEqualTo(InterviewPhase.FINISHED);
    }

    @Test
    @DisplayName("FINISHED 状态不可逆")
    void finishedIsTerminal() {
        assertThat(nextPhase(InterviewPhase.FINISHED, 1, false, false))
                .isEqualTo(InterviewPhase.FINISHED);
    }

    @Test
    @DisplayName("TECHNICAL 内继续保持（未超轮数、无标记）")
    void technicalContinuesWithinLimit() {
        assertThat(nextPhase(InterviewPhase.TECHNICAL, 5, false, false))
                .isEqualTo(InterviewPhase.TECHNICAL);
    }
}
