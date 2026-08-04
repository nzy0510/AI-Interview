import request from '@/utils/request';

/**
 * 获取当前用户可见的岗位摘要列表。
 * 返回公共岗位和用户自己的私有岗位，
 * 每个岗位附带历史记录数(historyCount)和简历画像状态。
 */
export const getVisiblePositionsAPI = (options = {}) => {
    return request({ url: '/positions/visible', method: 'get', ...options });
};
