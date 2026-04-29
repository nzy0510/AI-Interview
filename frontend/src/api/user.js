import request from '@/utils/request';

export const loginAPI = (data) => {
    return request({
        url: '/user/login',
        method: 'post',
        data
    });
};

export const registerAPI = (data) => {
    return request({
        url: '/user/register',
        method: 'post',
        data
    });
};

export const sendCodeAPI = (data) => {
    return request({
        url: '/user/send-code',
        method: 'post',
        data
    });
};

export const forgotPasswordAPI = (data) => {
    return request({
        url: '/user/forgot-password',
        method: 'post',
        data
    });
};

export const resetPasswordAPI = (data) => {
    return request({
        url: '/user/reset-password',
        method: 'post',
        data
    });
};

export const getMentorInsightAPI = () => {
    return request({ url: '/user/mentor-insight', method: 'get' });
};

export const getKnowledgeCoverageAPI = () => {
    return request({ url: '/user/knowledge-coverage', method: 'get' });
};
