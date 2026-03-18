import axios from 'axios';
import { ElMessage } from 'element-plus';
import router from '@/router';

// Create Axios Instance
const request = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api', // Backend base URL
    timeout: 30000 // 30s timeout
});

// Request Interceptor
request.interceptors.request.use(
    config => {
        // Add Authorization header token if it exists in localStorage
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    error => {
        return Promise.reject(error);
    }
);

// Response Interceptor
request.interceptors.response.use(
    response => {
        const res = response.data;
        // If our backend custom code is 200, it's successful
        if (res.code === 200) {
            return res.data;
        } else {
            // General Error
            ElMessage.error(res.msg || 'Error');
            return Promise.reject(new Error(res.msg || 'Error'));
        }
    },
    error => {
        console.error('API Error:', error);
        
        if (error.response && error.response.status === 401) {
            ElMessage.error("未登录或 Token 过期，请重新登录！");
            localStorage.removeItem('token');
            router.push('/login');
        } else {
            ElMessage.error(error.message || '网络请求异常，请检查后端是否启动');
        }
        return Promise.reject(error);
    }
);

export default request;
