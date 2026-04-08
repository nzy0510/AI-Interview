import request from '@/utils/request';

export const startInterviewAPI = (data) => {
    return request({ url: '/interview/start', method: 'post', data });
};

export const finishInterviewAPI = (data) => {
    return request({ url: '/interview/finish', method: 'post', data });
};

export const getHistoryListAPI = () => {
    return request({ url: '/history/list', method: 'get' });
};

export const getHistoryDetailAPI = (id) => {
    return request({ url: `/history/detail/${id}`, method: 'get' });
};
