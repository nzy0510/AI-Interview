import request from '@/utils/request';

export const startInterviewAPI = (data) => {
    return request({ url: '/interview/start', method: 'post', data });
};

export const finishInterviewAPI = (data) => {
    return request({ url: '/interview/finish', method: 'post', data });
};

export const getJobDetailAPI = (jobId, options = {}) => {
    return request({ url: `/jobs/${jobId}`, method: 'get', ...options });
};

export const retryJobAPI = (jobId) => {
    return request({ url: `/jobs/${jobId}/retry`, method: 'post' });
};

export const getInterviewReportAPI = (recordId, options = {}) => {
    return request({ url: `/interview/reports/${recordId}`, method: 'get', ...options });
};

export const discardInterviewAPI = (data) => {
    return request({ url: '/interview/discard', method: 'post', data });
};

export const getHistoryListAPI = () => {
    return request({ url: '/history/list', method: 'get' });
};

export const getHistoryDetailAPI = (id) => {
    return request({ url: `/history/detail/${id}`, method: 'get' });
};
