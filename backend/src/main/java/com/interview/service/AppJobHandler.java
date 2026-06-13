package com.interview.service;

import com.interview.entity.AppJob;

public interface AppJobHandler {
    String jobType();

    void handle(AppJob job);
}
