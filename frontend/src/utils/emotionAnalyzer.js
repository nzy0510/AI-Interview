/**
 * emotionAnalyzer.js
 * 封装 face-api.js 的情感分析能力
 * - 浏览器端运行，基于 TensorFlow.js
 * - 支持 7 种情绪识别：neutral, happy, sad, angry, fearful, disgusted, surprised
 * - 自信指数推算：基于 neutral + happy 占比
 */
import * as faceapi from 'face-api.js'

// 模型 CDN 地址（避免本地存储大文件）
const MODEL_URL = 'https://cdn.jsdelivr.net/gh/justadudewhohacks/face-api.js@master/weights'

let modelsLoaded = false

/**
 * 初始化 face-api.js 模型（首次调用时加载，后续跳过）
 */
export async function initModels() {
  if (modelsLoaded) return true
  try {
    await Promise.all([
      faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL),
      faceapi.nets.faceExpressionNet.loadFromUri(MODEL_URL),
      faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL)
    ])
    modelsLoaded = true
    console.log('[EmotionAnalyzer] 模型加载完成')
    return true
  } catch (err) {
    console.error('[EmotionAnalyzer] 模型加载失败:', err)
    return false
  }
}

/**
 * 分析单帧视频画面，返回情绪数据
 * @param {HTMLVideoElement} videoEl - 摄像头 video 元素
 * @returns {Object|null} 情绪数据 { expressions, confidence, landmarks }
 */
export async function analyzeFrame(videoEl) {
  if (!modelsLoaded || !videoEl || videoEl.readyState < 2) return null

  try {
    const detection = await faceapi
      .detectSingleFace(videoEl, new faceapi.TinyFaceDetectorOptions({ inputSize: 224, scoreThreshold: 0.5 }))
      .withFaceLandmarks()
      .withFaceExpressions()

    if (!detection) return null

    const expressions = detection.expressions
    const confidence = calcConfidence(expressions)

    return {
      expressions: {
        neutral: round(expressions.neutral),
        happy: round(expressions.happy),
        sad: round(expressions.sad),
        angry: round(expressions.angry),
        fearful: round(expressions.fearful),
        disgusted: round(expressions.disgusted),
        surprised: round(expressions.surprised)
      },
      confidence,
      dominantEmotion: getDominant(expressions)
    }
  } catch {
    return null
  }
}

/**
 * 根据情绪分布计算自信指数 (0-1)
 * 自信 = neutral 和 happy 的加权和，减去 fearful 和 sad 的惩罚
 */
function calcConfidence(expressions) {
  const score = (expressions.neutral * 0.4) +
                (expressions.happy * 0.4) +
                (expressions.surprised * 0.1) -
                (expressions.fearful * 0.3) -
                (expressions.sad * 0.2) -
                (expressions.angry * 0.1)
  return round(Math.max(0, Math.min(1, score + 0.3))) // 归一化到 0-1，基线偏移 0.3
}

/**
 * 获取主导情绪
 */
function getDominant(expressions) {
  let max = 0, dominant = 'neutral'
  for (const [key, val] of Object.entries(expressions)) {
    if (val > max) { max = val; dominant = key }
  }
  return dominant
}

function round(v) {
  return Math.round(v * 100) / 100
}

// ========== 面试级别汇总 ==========

/**
 * 对整场面试的情绪采样数据做统计汇总
 * @param {Array} timeline - 每次采样的 { timestamp, expressions, confidence } 数组
 * @returns {Object} 汇总数据，可直接存入 emotionJson
 */
export function getEmotionSummary(timeline) {
  if (!timeline || timeline.length === 0) {
    return { avgConfidence: 0, dominantEmotion: 'unknown', emotionDistribution: {}, sampleCount: 0 }
  }

  // 求各情绪的平均值
  const keys = ['neutral', 'happy', 'sad', 'angry', 'fearful', 'disgusted', 'surprised']
  const avgExpressions = {}
  keys.forEach(k => {
    const sum = timeline.reduce((s, t) => s + (t.expressions?.[k] || 0), 0)
    avgExpressions[k] = round(sum / timeline.length)
  })

  // 求平均自信指数
  const avgConfidence = round(timeline.reduce((s, t) => s + (t.confidence || 0), 0) / timeline.length)

  // 求主导情绪
  const dominantEmotion = getDominant(avgExpressions)

  return {
    avgConfidence,
    dominantEmotion,
    emotionDistribution: avgExpressions,
    sampleCount: timeline.length,
    timeline: timeline.map(t => ({
      ts: t.timestamp,
      conf: t.confidence,
      dom: t.dominantEmotion
    }))
  }
}

// 情绪中英文映射（用于 UI 显示）
export const EMOTION_LABELS = {
  neutral: '平静',
  happy: '积极',
  sad: '低落',
  angry: '紧张',
  fearful: '焦虑',
  disgusted: '不适',
  surprised: '惊讶'
}
