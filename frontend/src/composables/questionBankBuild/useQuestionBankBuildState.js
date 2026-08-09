import { computed, ref } from 'vue'
import {
  isQuestionBankCandidateFinalizable,
  isQuestionBankCandidateRepairVerified,
  wasQuestionBankCandidateRepairAttempted
} from '@/utils/knowledgeWorkspace'

export function createQuestionBankBuildState() {
  const builds = ref([])
  const activeBuildId = ref(null)
  const activeBuild = ref(null)
  const candidates = ref([])
  const loading = ref(false)
  const candidatesLoading = ref(false)
  const actionLoading = ref('')
  const error = ref(null)
  const activeCandidates = computed(() => candidates.value)
  const exceptionCandidates = computed(() => candidates.value.filter((candidate) => {
    const reviewStatus = String(candidate?.status || candidate?.reviewStatus || '').toUpperCase()
    if (['ACCEPTED', 'REJECTED'].includes(reviewStatus)) return false
    return !['AUTO_PASS', 'AUTO_REJECT', 'SKIPPED'].includes(
      String(candidate?.machineReviewStatus || '').toUpperCase())
  }))
  const finalizableCandidates = computed(() => candidates.value.filter(isQuestionBankCandidateFinalizable))
  const repairedCandidates = computed(() => candidates.value.filter(wasQuestionBankCandidateRepairAttempted))
  const successfullyRepairedCandidates = computed(() => candidates.value.filter(isQuestionBankCandidateRepairVerified))
  const buildCompleted = computed(() => ['COMPLETED', 'SUCCEEDED'].includes(
    String(activeBuild.value?.status || '').toUpperCase()))
  const canReview = computed(() => {
    const stage = String(activeBuild.value?.stage || '').toUpperCase()
    return Boolean(activeBuildId.value && buildCompleted.value
      && (!stage || stage === 'READY_FOR_FINAL_REVIEW'))
  })
  const canFinalize = computed(() => Boolean(
    canReview.value
    && String(activeBuild.value?.stage || '').toUpperCase() === 'READY_FOR_FINAL_REVIEW'
    && finalizableCandidates.value.length > 0
  ))

  const clear = () => {
    builds.value = []
    activeBuild.value = null
    activeBuildId.value = null
    candidates.value = []
    loading.value = false
    candidatesLoading.value = false
    error.value = null
  }

  return {
    builds,
    activeBuildId,
    activeBuild,
    candidates,
    loading,
    candidatesLoading,
    actionLoading,
    error,
    activeCandidates,
    exceptionCandidates,
    finalizableCandidates,
    repairedCandidates,
    successfullyRepairedCandidates,
    buildCompleted,
    canReview,
    canFinalize,
    clear
  }
}
