import { ref } from 'vue'

export function useInterviewOrchestration() {
  const orchestrationDecision = ref(null)

  const setOrchestrationDecision = (decision) => {
    orchestrationDecision.value = decision
      ? { ...decision, tools: [...(decision.tools || [])] }
      : null
  }

  const resetOrchestrationDecision = () => {
    orchestrationDecision.value = null
  }

  return {
    orchestrationDecision,
    setOrchestrationDecision,
    resetOrchestrationDecision
  }
}
