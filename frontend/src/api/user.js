import request from '@/utils/request';

// API Definition for User
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
