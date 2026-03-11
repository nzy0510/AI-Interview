import request from '@/utils/request';

// API Definition for Interview
export const startInterviewAPI = (data) => {
    return request({
        url: '/interview/start',
        method: 'post',
        data
    });
};

export const finishInterviewAPI = (data) => {
    return request({
        url: '/interview/finish',
        method: 'post',
        data
    });
};

// Notes: The chatStream endpoint is SSE, so it won't use this standard axios instance directly.
