import request from '@/utils/request';
import { withAuthHeaders } from '@/utils/auth';

/**
 * Phase 2 岗位隔离简历 API。
 * 所有接口强制要求 positionId；不传返回 400。
 */

/** 上传并解析 PDF 简历（按岗位覆盖） */
export const parseResumeAPI = (formData) => {
    return request({
        url: '/resume/parse',
        method: 'post',
        data: formData
        // 不手动设置 Content-Type，让 axios 自动带 boundary
    });
};

/** 获取指定岗位的简历画像（含岗位元信息） */
export const getResumeProfileAPI = (positionId) => {
    return request({ url: '/resume/profile', method: 'get', params: { positionId } });
};

/** 删除指定岗位的简历画像 */
export const deleteResumeProfileAPI = (positionId) => {
    return request({ url: '/resume/profile', method: 'delete', params: { positionId } });
};

/** 获取带 Auth headers 的原生 fetch（用于静默请求，避免拦截器 ElMessage.error） */
export const fetchResumeProfile = async (positionId) => {
    const baseUrl = (import.meta.env.VITE_API_BASE_URL || '') + '/api/resume/profile?positionId=' + positionId;
    const resp = await fetch(baseUrl, { headers: withAuthHeaders() });
    if (!resp.ok) return null;
    const result = await resp.json();
    return result.code === 200 && result.data ? result.data : null;
};
