package com.interview.service.questionbank;

import com.alibaba.fastjson2.JSON;
import com.interview.entity.AppJob;
import com.interview.service.AppJobHandler;
import com.interview.service.AppJobService;
import org.springframework.stereotype.Service;

@Service
public class AtomGenerationJobHandler implements AppJobHandler {

    private final KnowledgeAtomWorkflowService workflowService;
    private final AppJobService appJobService;

    public AtomGenerationJobHandler(KnowledgeAtomWorkflowService workflowService,
                                    AppJobService appJobService) {
        this.workflowService = workflowService;
        this.appJobService = appJobService;
    }

    @Override
    public String jobType() {
        return KnowledgeAtomWorkflowService.JOB_TYPE_GENERATE_ATOMS;
    }

    @Override
    public void handle(AppJob job) {
        appJobService.updateRunningJob(job.getId(), job.getClaimedBy(), "GENERATING_ATOMS", 50);
        KnowledgeAtomGenerationResult result = workflowService.generateAtomsForJob(job);
        job.setResultJson(JSON.toJSONString(result));
        appJobService.updateRunningJob(job.getId(), job.getClaimedBy(), "ATOMS_GENERATED", 95);
    }
}
